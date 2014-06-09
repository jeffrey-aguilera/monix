package monifu.reactive.channels

import monifu.reactive.{Observer, Channel}
import monifu.reactive.observers.BufferedObserver
import monifu.concurrent.Scheduler
import monifu.reactive.subjects.BehaviorSubject
import monifu.reactive.api.BufferPolicy
import monifu.reactive.api.BufferPolicy.Unbounded
import monifu.reactive.observables.GenericObservable
import monifu.concurrent.locks.SpinLock

/**
 * A `BehaviorChannel` is a [[Channel]] that uses an underlying
 * [[monifu.reactive.subjects.BehaviorSubject BehaviorSubject]].
 */
final class BehaviorChannel[T] private (initialValue: T, policy: BufferPolicy, s: Scheduler) extends Channel[T] with GenericObservable[T] {
  implicit val scheduler = s

  private[this] val lock = SpinLock()
  private[this] val subject = BehaviorSubject(initialValue)
  private[this] val channel = BufferedObserver(subject, policy)

  private[this] var isDone = false
  private[this] var lastValue = initialValue
  private[this] var errorThrown = null : Throwable

  def subscribeFn(observer: Observer[T]): Unit = {
    subject.subscribeFn(observer)
  }

  def pushNext(elems: T*): Unit = lock.enter {
    if (!isDone)
      for (elem <- elems) {
        lastValue = elem
        channel.onNext(elem)
      }
  }

  def pushComplete() = lock.enter {
    if (!isDone) {
      isDone = true
      channel.onComplete()
    }
  }

  def pushError(ex: Throwable) = lock.enter {
    if (!isDone) {
      isDone = true
      errorThrown = ex
      channel.onError(ex)
    }
  }

  def :=(update: T): Unit = pushNext(update)

  def apply(): T = lock.enter {
    if (errorThrown ne null)
      throw errorThrown
    else
      lastValue
  }
}

object BehaviorChannel {
  def apply[T](initial: T, bufferPolicy: BufferPolicy = Unbounded)(implicit s: Scheduler): BehaviorChannel[T] =
    new BehaviorChannel[T](initial, bufferPolicy, s)
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import kotlinx.collections.immutable.PersistentMap
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlin.coroutines.cancellation.CancellationException
import fleet.multiplatform.shims.ConcurrentHashMap
import kotlin.coroutines.coroutineContext
import kotlin.reflect.KClass

typealias BifurcanVector<T> = fleet.util.bifurcan.List<T>
typealias IBifurcanVector<T> = fleet.util.bifurcan.List<T>
typealias BifurcanSet<T> = SortedSet<T>
typealias IBifurcanSet<T> = SortedSet<T>

fun <K, V : Any> PersistentMap<K, V>.update(k: K, f: (V?) -> V?): PersistentMap<K, V> {
  val vPrime = f(this[k])
  return when {
    vPrime == null -> remove(k)
    else -> put(k, vPrime)
  }
}

private fun <T, U> memoize(f: (T) -> U): (T) -> U {
  val cache = ConcurrentHashMap<T, U>()
  return { t -> cache.computeIfAbsent(t, f) }
}

fun <T> constantly(f: () -> T): () -> T {
  val r = f()
  return { r }
}

fun IBifurcanVector<*>.isEmpty(): Boolean {
  return size() == 0L
}

fun IBifurcanVector<*>.isNotEmpty(): Boolean {
  return !isEmpty()
}


private interface Mult<T> {
  fun tap(sink: SendChannel<T>)
}

suspend fun <T> SendChannel<T>.trySendSuspending(t: T): Boolean {
  return try {
    send(t)
    true
  }
  catch (e: CancellationException) {
    coroutineContext.job.ensureActive()
    false
  }
  catch (e: ClosedSendChannelException) {
    false
  }
}

fun <V> List<V>.toBifurcan(): BifurcanVector<V> {
  return BifurcanVector.from(this)
}

private fun <T, K> Sequence<T>.chunkBy(keyFn: (T) -> K): Sequence<List<T>> {
  val iter = this.iterator()
  return Sequence {
    iterator {
      var l = ArrayList<T>()
      var k: K? = null
      var first = true
      while (iter.hasNext()) {
        val n = iter.next()
        val nk = keyFn(n)
        if (first || nk == k) {
          l.add(n)
          first = false
        }
        else {
          yield(l)
          l = ArrayList()
          l.add(n)
        }
        k = nk
      }
      if (l.isNotEmpty()) {
        yield(l)
      }
    }
  }
}

fun <T : Any> Throwable.causeOfType(klass: KClass<T>): T? {
  // TODO handle cycles
  return when {
    klass.isInstance(this) -> this as T
    else -> cause?.causeOfType(klass)
  }
}

fun Throwable.causes(causeCount: Int = 5): Iterable<Throwable> {
  return generateSequence(this) { it.cause }.take(causeCount).asIterable()
}

inline fun <reified T: Any> Throwable.causeOfType() : T? {
  return this.causeOfType(T::class)
}

inline fun <reified T : Any> Any?.safeAs(): T? = this as? T
inline fun <reified T : Any> Any?.cast(): T = this as T
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import kotlinx.collections.immutable.PersistentMap
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KClass

fun <K, V : Any> PersistentMap<K, V>.update(k: K, f: (V?) -> V?): PersistentMap<K, V> {
  val vPrime = f(this[k])
  return when {
    vPrime == null -> remove(k)
    else -> put(k, vPrime)
  }
}

fun <T> constantly(f: () -> T): () -> T {
  val r = f()
  return { r }
}

suspend fun <T> SendChannel<T>.trySendSuspending(t: T): Boolean {
  return try {
    send(t)
    true
  }
  catch (_: CancellationException) {
    currentCoroutineContext().job.ensureActive()
    false
  }
  catch (_: ClosedSendChannelException) {
    false
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
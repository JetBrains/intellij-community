// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.multiplatform.shims


import kotlinx.coroutines.ThreadContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

// Copied from kotlinx.coroutines, works on top of multiplatform ThreadLocal
fun <T> ThreadLocal<T>.asContextElement(value: T = get()): ThreadContextElement<T> =
  ThreadLocalElement(value, this)

// top-level data class for a nicer out-of-the-box toString representation and class name
@PublishedApi
internal data class ThreadLocalKey(private val threadLocal: ThreadLocal<*>) : CoroutineContext.Key<ThreadLocalElement<*>>

internal class ThreadLocalElement<T>(
  private val value: T,
  private val threadLocal: ThreadLocal<T>
) : ThreadContextElement<T> {
  override val key: CoroutineContext.Key<*> = ThreadLocalKey(threadLocal)

  override fun updateThreadContext(context: CoroutineContext): T {
    val oldState = threadLocal.get()
    threadLocal.set(value)
    return oldState
  }

  override fun restoreThreadContext(context: CoroutineContext, oldState: T) {
    threadLocal.set(oldState)
  }

  // this method is overridden to perform value comparison (==) on key
  override fun minusKey(key: CoroutineContext.Key<*>): CoroutineContext {
    return if (this.key == key) EmptyCoroutineContext else this
  }

  // this method is overridden to perform value comparison (==) on key
  public override operator fun <E : CoroutineContext.Element> get(key: CoroutineContext.Key<E>): E? =
    @Suppress("UNCHECKED_CAST")
    if (this.key == key) this as E else null

  override fun toString(): String = "ThreadLocal(value=$value, threadLocal = $threadLocal)"
}
/*
This is free and unencumbered software released into the public domain.
Anyone is free to copy, modify, publish, use, compile, sell, or
distribute this software, either in source code form or as a compiled
binary, for any purpose, commercial or non-commercial, and by any
means.
In jurisdictions that recognize copyright laws, the author or authors
of this software dedicate any and all copyright interest in the
software to the public domain. We make this dedication for the benefit
of the public at large and to the detriment of our heirs and
successors. We intend this dedication to be an overt act of
relinquishment in perpetuity of all present and future rights to this
software under copyright law.
THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
OTHER DEALINGS IN THE SOFTWARE.
For more information, please refer to <https://unlicense.org>
*/

// https://gist.github.com/elizarov/9a48b9709ffd508909d34fab6786acfe
// https://github.com/Kotlin/kotlinx.coroutines/issues/1686#issuecomment-825547551

package com.intellij.platform.diagnostic.telemetry.exporters

import com.intellij.concurrency.IntelliJContextElement
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

internal suspend fun <T> Mutex.withReentrantLock(block: suspend () -> T): T {
  val key = ReentrantMutexContextKey(this)
  // call block directly when this mutex is already locked in the context
  if (coroutineContext[key] != null) {
    return block()
  }

  // otherwise, add it to the context and lock the mutex
  return withContext(ReentrantMutexContextElement(key)) {
    withLock { block() }
  }
}

private class ReentrantMutexContextElement(override val key: ReentrantMutexContextKey) : CoroutineContext.Element, IntelliJContextElement {

  override fun produceChildElement(parentContext: CoroutineContext, isStructured: Boolean): IntelliJContextElement = this
}

private data class ReentrantMutexContextKey(@JvmField val mutex: Mutex) : CoroutineContext.Key<ReentrantMutexContextElement>
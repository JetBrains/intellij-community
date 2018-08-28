// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.concurrency

import com.intellij.openapi.application.ApplicationManager
import kotlin.coroutines.experimental.AbstractCoroutineContextElement
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.ContinuationInterceptor
import kotlin.coroutines.experimental.CoroutineContext

/**
 * Doesn't dispatch continuation anywhere same as [kotlinx.coroutines.experimental.Unconfined], but asserts that current thread is EDT
 */
object UnconfinedEdt : AbstractCoroutineContextElement(ContinuationInterceptor), ContinuationInterceptor {

  override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> = SimpleEdtContinuation(continuation)

  private class SimpleEdtContinuation<T>(private val original: Continuation<T>) : Continuation<T> {

    override val context: CoroutineContext get() = original.context

    override fun resume(value: T) {
      ApplicationManager.getApplication().assertIsDispatchThread()
      original.resume(value)
    }

    override fun resumeWithException(exception: Throwable) {
      ApplicationManager.getApplication().assertIsDispatchThread()
      original.resumeWithException(exception)
    }
  }
}

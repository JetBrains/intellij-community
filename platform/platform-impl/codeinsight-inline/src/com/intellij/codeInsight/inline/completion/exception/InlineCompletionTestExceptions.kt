// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.exception

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import kotlin.reflect.KClass

/**
 * If you'd like to test an exception for the inline completion pipeline, please use these exceptions.
 * They do not fail executors and properly processed.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
object InlineCompletionTestExceptions {

  /**
   * Creates an artificial expected exception. This exception is handled separately and doesn't fail the request executor.
   */
  @TestOnly
  fun createExpectedTestException(message: String): RuntimeException = TestException(message)

  @TestOnly
  fun getExceptionClass(): KClass<out RuntimeException> = TestException::class

  internal fun isExpectedTestException(e: Throwable): Boolean {
    return e is TestException || e.cause is TestException
  }

  private class TestException(message: String) : RuntimeException(message)
}

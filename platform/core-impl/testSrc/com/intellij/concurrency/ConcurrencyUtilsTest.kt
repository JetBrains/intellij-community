// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.concurrency

import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.progress.ProgressManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.job
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

@TestApplication
internal class ConcurrencyUtilsTest {
  @Test
  fun `installs an empty indicator when no indicator or context job is available`() {
    assertNull(ProgressIndicatorProvider.getGlobalProgressIndicator())

    val result = ConcurrencyUtils.runWithIndicatorOrContextCancellation { indicator ->
      assertInstanceOf(EmptyProgressIndicator::class.java, indicator)
      assertSame(ProgressIndicatorProvider.getGlobalProgressIndicator(), indicator)
      "result"
    }

    assertEquals("result", result)
    assertNull(ProgressIndicatorProvider.getGlobalProgressIndicator())
  }

  @Test
  fun `uses the installed indicator`() {
    val indicator = EmptyProgressIndicator()

    ProgressManager.getInstance().executeProcessUnderProgress(
      {
        val result = ConcurrencyUtils.runWithIndicatorOrContextCancellation { actualIndicator ->
          assertSame(indicator, actualIndicator)
          "result"
        }

        assertEquals("result", result)
      },
      indicator,
    )

    assertNull(ProgressIndicatorProvider.getGlobalProgressIndicator())
  }

  @Test
  fun `installs an indicator for the context job when no indicator is available`(): Unit = timeoutRunBlocking {
    assertNull(ProgressIndicatorProvider.getGlobalProgressIndicator())
    assertSame(Cancellation.currentJob(), coroutineContext.job)

    val result = ConcurrencyUtils.runWithIndicatorOrContextCancellation { indicator ->
      assertSame(ProgressIndicatorProvider.getGlobalProgressIndicator(), indicator)
      "result"
    }

    assertEquals("result", result)
    assertNull(ProgressIndicatorProvider.getGlobalProgressIndicator())
  }
}

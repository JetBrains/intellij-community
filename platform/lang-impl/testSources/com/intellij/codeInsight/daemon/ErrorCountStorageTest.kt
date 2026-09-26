// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon

import com.intellij.codeInsight.daemon.impl.ErrorCountStorage
import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.lang.annotation.HighlightSeverity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ErrorCountStorageTest {
  private val storage = ErrorCountStorage()
  private val mockContext = MockContext("ctx1")

  @Test
  fun `context`() {
    storage.incErrorCount(HighlightSeverity.WARNING, mockContext, 1)
    assertEquals(1, storage.getErrorCount(HighlightSeverity.WARNING, mockContext))
  }

  @Test
  fun `inc and dec`() {
    storage.incErrorCount(HighlightSeverity.WARNING, mockContext, 1)
    storage.incErrorCount(HighlightSeverity.WARNING, mockContext, -1)
    assertEquals(0, storage.getErrorCount(HighlightSeverity.WARNING, mockContext))
  }

  @Test
  fun `inc and dec too much`() {
    storage.incErrorCount(HighlightSeverity.WARNING, mockContext, 1)
    storage.incErrorCount(HighlightSeverity.WARNING, mockContext, -10)
    assertEquals(0, storage.getErrorCount(HighlightSeverity.WARNING, mockContext))
  }

  @Test
  fun `no-context`() {
    storage.incErrorCount(HighlightSeverity.WARNING, null, 1)
    assertEquals(1, storage.getErrorCount(HighlightSeverity.WARNING, mockContext))
  }

  @Test
  fun `no-context and context`() {
    storage.incErrorCount(HighlightSeverity.WARNING, null, 1)
    storage.incErrorCount(HighlightSeverity.WARNING, mockContext, 1)

    assertEquals(2, storage.getErrorCount(HighlightSeverity.WARNING, mockContext))
  }

  private data class MockContext(val name: String) : CodeInsightContext
}
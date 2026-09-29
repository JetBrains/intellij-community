// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation

import com.intellij.openapi.components.service
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@TestApplication
class CtrlMouseHandlerComputingTest {
  private val project by projectFixture()
  private val handler get() = project.service<CtrlMouseHandler2>()

  @Test
  fun `marker is set inside computing only`() {
    val handler = handler
    assertFalse(handler.isComputing())
    val result = handler.computing { handler.isComputing() }
    assertTrue(result)
    assertFalse(handler.isComputing())
  }

  @Test
  fun `marker is reset after throw`() {
    val handler = handler
    assertThrows<IllegalStateException> {
      handler.computing { error("boom") }
    }
    assertFalse(handler.isComputing())
  }

  @Test
  fun `nested calls restore marker`() {
    val handler = handler
    val seen = handler.computing {
      val inner = handler.computing { handler.isComputing() }
      listOf(inner, handler.isComputing())
    }
    assertEquals(listOf(true, true), seen)
    assertFalse(handler.isComputing())
  }

  @Test
  fun `marker is per thread`() {
    val handler = handler
    var other = true
    handler.computing {
      val thread = Thread { other = handler.isComputing() }
      thread.start()
      thread.join()
    }
    assertFalse(other)
  }
}

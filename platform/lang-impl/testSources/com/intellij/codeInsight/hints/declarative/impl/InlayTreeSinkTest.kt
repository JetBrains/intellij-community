// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase
import org.junit.Test

class InlayTreeSinkTest : LightPlatformCodeInsightFixture4TestCase() {
  @Test
  fun testAddSinglePresentationWithoutOptions() {
    val providerId = "my.provider"
    val sink = InlayTreeSinkImpl(providerId, mapOf(), false, false)
    val position = InlineInlayPosition(1, false)
    sink.addPresentation(position, hasBackground = false) {
      text("inlay text")
    }
    val inlayData = sink.finish()
    assertEquals(1, inlayData.size)
    val data = inlayData.single()
    assertEquals(providerId, data.providerId)
    assertEquals(false, data.disabled)
    assertEquals(false, data.hasBackground)
  }

  @Test
  fun testAddUnderNonExistingOptionThrowsException() {
    val providerId = "my.provider"
    val sink = InlayTreeSinkImpl(providerId, mapOf(), false, false)
    val position = InlineInlayPosition(1, false)
    UsefulTestCase.assertThrows(Throwable::class.java) {
      sink.whenOptionEnabled("random non-existing option") {
        sink.addPresentation(position, hasBackground = false) {
          text("inlay text")
        }
      }
    }
  }

  @Test
  fun testAddUnderExistingDisabledOptionIsNotAdded() {
    val providerId = "my.provider"
    val sink = InlayTreeSinkImpl(providerId, mapOf("option" to false), false, false)
    val position = InlineInlayPosition(1, false)
    sink.whenOptionEnabled("option") {
      sink.addPresentation(position, hasBackground = false) {
        text("inlay text")
      }
    }
    UsefulTestCase.assertEmpty(sink.finish())
  }

  @Test
  fun testAddUnderExistingEnabledOptionIsAdded() {
    val providerId = "my.provider"
    val sink = InlayTreeSinkImpl(providerId, mapOf("option" to true), false, false)
    val position = InlineInlayPosition(1, false)
    sink.whenOptionEnabled("option") {
      sink.addPresentation(position, hasBackground = false) {
        text("inlay text")
      }
    }
    UsefulTestCase.assertNotEmpty(sink.finish())
  }

  @Test
  fun testInPreviewDisabled() {
    val providerId = "my.provider"
    val sink = InlayTreeSinkImpl(providerId, mapOf("option" to true), true, true)
    val position = InlineInlayPosition(1, false)
    sink.addPresentation(position, hasBackground = false) {
      text("inlay text")
    }
    val data = sink.finish()
    UsefulTestCase.assertSize(1, data)
    val inlay = data.single()
    assertTrue(inlay.disabled)
  }
}
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.hints.declarative.HintColorKind
import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.InlayPayload
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.StringInlayActionPayload
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.extensionPointFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@TestApplication
internal class InlayTreeSinkTest {
  @Test
  fun testAddSinglePresentationWithoutOptions() {
    val providerId = "my.provider"
    val sink = createSink(emptyMap(), providerId = providerId)
    sink.addHint(hintFormat = HintFormat.default.withColorKind(HintColorKind.TextWithoutBackground))
    val inlayData = sink.finish()
    assertEquals(1, inlayData.size)
    val data = inlayData.single()
    assertEquals(providerId, data.providerId)
    assertFalse(data.disabled)
    assertEquals(HintColorKind.TextWithoutBackground, data.hintFormat.colorKind)
  }

  @Test
  fun testAddUnderNonExistingOptionThrowsException() {
    val sink = createSink(emptyMap())
    assertThrows<Throwable> {
      sink.whenOptionEnabled("random non-existing option") {
        sink.addHint()
      }
    }
  }

  @Test
  fun testAddUnderExistingDisabledOptionIsNotAdded() {
    val sink = createSink(mapOf("option" to false))
    sink.whenOptionEnabled("option") {
      sink.addHint()
    }
    assertEquals(emptyList<InlayData>(), sink.finish())
  }

  @Test
  fun testAddUnderExistingEnabledOptionIsAdded() {
    val sink = createSink(mapOf("option" to true))
    sink.whenOptionEnabled("option") {
      sink.addHint()
    }
    assertTrue(sink.finish().isNotEmpty())
  }

  @Test
  fun testInPreviewDisabled() {
    val sink = createSink(mapOf("option" to true), isInPreview = true, providerIsDisabled = true)
    sink.addHint()
    val data = sink.finish()
    assertEquals(1, data.size)
    assertTrue(data.single().disabled)
  }

  @Test
  fun testInPreviewDisabledOption() {
    val sink = createSink(mapOf("option" to false), isInPreview = true)
    sink.whenOptionEnabled("option") {
      sink.addHint()
    }
    val data = sink.finish()
    assertEquals(1, data.size)
    assertTrue(data.single().disabled)
  }

  @Nested
  inner class ObservingPayloadContributor {
    private val contributor by extensionPointFixture(DeclarativeInlayPayloadContributor.EP_NAME) { RecordingContributor() }

    @Test
    fun testContributorGetsNoOptionsOutsideOption() {
      val sink = createSink(mapOf("A" to true))
      sink.addHint()
      assertEquals(listOf(emptyList<String>()), contributor.recorded)
    }

    @Test
    fun testContributorGetsEnclosingOptions() {
      val sink = createSink(mapOf("A" to true, "B" to true))
      sink.whenOptionEnabled("A") {
        sink.whenOptionEnabled("B") {
          sink.addHint()
        }
      }
      assertEquals(listOf(listOf("A", "B")), contributor.recorded)
    }

    @Test
    fun testContributorGetsSiblingOptionAfterNestedBlock() {
      val sink = createSink(mapOf("A" to true, "B" to true, "C" to true))
      sink.whenOptionEnabled("A") {
        sink.whenOptionEnabled("B") {}
      }
      sink.whenOptionEnabled("C") {
        sink.addHint()
      }
      assertEquals(listOf(listOf("C")), contributor.recorded)
    }

    @Test
    fun testNullFromContributorKeepsPayloadsUnchanged() {
      val providerPayload = InlayPayload("provider", StringInlayActionPayload("value"))
      val sink = createSink(emptyMap())
      sink.addHint(listOf(providerPayload))
      assertEquals(listOf(providerPayload), sink.finish().single().payloads)
    }
  }

  @Nested
  inner class PayloadAddingContributor {
    private val contributor by extensionPointFixture(DeclarativeInlayPayloadContributor.EP_NAME) {
      RecordingContributor(InlayPayload("contributed", StringInlayActionPayload("value")))
    }

    @Test
    fun testContributedPayloadIsAdded() {
      val providerPayload = InlayPayload("provider", StringInlayActionPayload("value"))
      val sink = createSink(emptyMap())
      sink.addHint(listOf(providerPayload))
      assertEquals(setOf(providerPayload, contributor.payload), sink.finish().single().payloads.toSet())
    }
  }

  private fun createSink(
    enabledOptions: Map<String, Boolean>,
    isInPreview: Boolean = false,
    providerIsDisabled: Boolean = false,
    providerId: String = "my.provider",
  ): InlayTreeSinkImpl {
    return InlayTreeSinkImpl(providerId, enabledOptions, isInPreview, providerIsDisabled, Int::class.java, DeclarativeInlayHintsPass.passSourceId)
  }

  private fun InlayTreeSinkImpl.addHint(payloads: List<InlayPayload>? = null, hintFormat: HintFormat = HintFormat.default) {
    addPresentation(InlineInlayPosition(1, false), payloads, hintFormat = hintFormat) {
      text("inlay text")
    }
  }

  private class RecordingContributor(val payload: InlayPayload? = null) : DeclarativeInlayPayloadContributor {
    val recorded: MutableList<List<String>> = ArrayList()

    override fun contribute(activeOptionIds: List<String>): InlayPayload? {
      recorded.add(activeOptionIds.toList())
      return payload
    }
  }
}

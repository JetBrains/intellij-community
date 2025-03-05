// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.hints.declarative.*
import com.intellij.codeInsight.hints.declarative.impl.inlayRenderer.DeclarativeInlayRendererBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import junit.framework.TestCase
import org.junit.Test

class DeclarativeInlayHintsPassTest : DeclarativeInlayHintPassTestBase() {
  @Test
  fun testAddInlay() {
    myFixture.configureByText("test.txt", "my content of file")
    val passInfo = InlayProviderPassInfo(object : InlayHintsProvider {
      override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector {
        return object : SharedBypassCollector {
          var added = false
          override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
            if (!added) {
              added = true
              sink.addPresentation(InlineInlayPosition(2, true), hasBackground = true) {
                text("inlay text")
              }
            }
          }
        }
      }
    }, "test.inlay.provider", emptyMap())
    val pass = createPass(passInfo)

    collectAndApplyPass(pass)

    assertEquals(listOf(TextInlayPresentationEntry("inlay text", clickArea = null)), getInlineInlays().single().getEntries())
  }

  @Test
  fun testAddAndUpdateAndRemoveAboveLineInlay() {
    myFixture.configureByText("test.txt", "my content of file")
    var inlayText = "first"
    val provider = StoredHintsProvider()
    val passInfo = InlayProviderPassInfo(provider, "test.inlay.provider", emptyMap())
    val hintAdder: (InlayTreeSink.() -> Unit) = {
      addPresentation(AboveLineIndentedPosition(0, 0, 0), hintFormat = HintFormat.default) {
        text(inlayText)
      }
    }
    // add inlay
    provider.hintAdder = hintAdder
    runPass(passInfo)
    assertEquals(listOf("first"), getBlockInlays().map { it.toText() })

    // update inlay
    inlayText = "second"
    provider.hintAdder = hintAdder
    runPass(passInfo)
    assertEquals(listOf("second"), getBlockInlays().map { it.toText() })

    // remove inlay
    runPass(passInfo)
    assertSize(0, getBlockInlays())
  }

  private fun Inlay<out DeclarativeInlayRendererBase<*>>.getEntries(): List<InlayPresentationEntry> {
    val presentationList = renderer.presentationLists.single()
    return presentationList.getEntries().toList()
  }

  @Test
  fun testInlayGetDeletedIfNotAddedOnNextRound() {
    myFixture.configureByText("test.txt", "my content of file")
    val provider = StoredHintsProvider()
    val providerInfo = InlayProviderPassInfo(provider, "test.inlay.provider", emptyMap())
    provider.hintAdder = {
      addPresentation(InlineInlayPosition(2, true), hasBackground = true) {
        text("inlay text")
      }
    }

    collectAndApplyPass(createPass(providerInfo))

    assertEquals(listOf(TextInlayPresentationEntry("inlay text", clickArea = null)), getInlineInlays().single().getEntries())
    assertNull(provider.hintAdder) // make sure no inlay is added on the next pass

    collectAndApplyPass(createPass(providerInfo))
    assertEquals(emptyList<Any>(), getInlineInlays())
  }

  @Test
  fun testInlayAtTheSameOffsetIsModifiedOnNextRound() {
    myFixture.configureByText("test.txt", "my content of file")
    val provider = StoredHintsProvider()
    val providerInfo = InlayProviderPassInfo(provider, "test.inlay.provider", emptyMap())
    provider.hintAdder = {
      addPresentation(InlineInlayPosition(2, true), hasBackground = true) {
        text("inlay text")
      }
    }

    collectAndApplyPass(createPass(providerInfo))

    val inlay = getInlineInlays().single()
    assertEquals(listOf(TextInlayPresentationEntry("inlay text", clickArea = null)), inlay.getEntries())
    provider.hintAdder = {
      addPresentation(InlineInlayPosition(2, true), hasBackground = true) {
        text("new text")
      }
    }

    collectAndApplyPass(createPass(providerInfo))
    val newInlay = getInlineInlays().single()
    TestCase.assertSame(inlay, newInlay)
    assertEquals(listOf(TextInlayPresentationEntry("new text", clickArea = null)), newInlay.getEntries())
  }

  @Test
  fun testMultipleWithDifferentPriorityAddedAtTheSameOffsetAreSorted() {
    myFixture.configureByText("test.txt", "my content of file")
    val provider = StoredHintsProvider()
    val providerInfo = InlayProviderPassInfo(provider, "test.inlay.provider", emptyMap())
    provider.hintAdder = {
      addPresentation(InlineInlayPosition(2, true, priority = 1), hasBackground = true) {
        text("1")
      }
      addPresentation(InlineInlayPosition(2, true, priority = 2), hasBackground = true) {
        text("2")
      }
      addPresentation(InlineInlayPosition(2, true, priority = 1), hasBackground = true) {
        text("3")
      }
      addPresentation(InlineInlayPosition(2, true, priority = 2), hasBackground = true) {
        text("4")
      }
    }

    collectAndApplyPass(createPass(providerInfo))

    val entries = getInlineInlays().map { it.getEntries().single() }
    assertEquals(listOf(
      TextInlayPresentationEntry("2", clickArea = null),
      TextInlayPresentationEntry("4", clickArea = null),
      TextInlayPresentationEntry("1", clickArea = null),
      TextInlayPresentationEntry("3", clickArea = null),
    ), entries)
  }

  // IJPL-160830
  @Test
  fun testInlayTooltipIsUpdatedWhenInlayIsReused() {
    myFixture.configureByText("test.txt", "content")
    val provider = StoredHintsProvider()
    val providerInfo = InlayProviderPassInfo(provider, "test.inlay.provider", emptyMap())

    fun addAndCheckInlayWithTooltip(tooltip: String?) {
      provider.hintAdder = {
        addPresentation(InlineInlayPosition(2, true), hintFormat = HintFormat.default, tooltip = tooltip) {
          text("text")
        }
      }
      collectAndApplyPass(createPass(providerInfo))
      assertEquals(tooltip, getInlineInlays().single().renderer.presentationList.model.tooltip)
    }

    addAndCheckInlayWithTooltip("1")
    // the hint is inserted at the same offset, and so the underlying editor inlay should be reused, and it's renderer updated
    addAndCheckInlayWithTooltip("2")
  }

  @Test
  fun testUpdateEnabled() {
    myFixture.configureByText("test.txt", "my content of file")
    val provider = StoredHintsProvider()
    val providerInfo = InlayProviderPassInfo(provider, "test.inlay.provider", emptyMap())
    provider.hintAdder = {
      addPresentation(InlineInlayPosition(2, true, priority = 1), hasBackground = true) {
        text("1")
      }
    }

    collectAndApplyPass(createPass(providerInfo, isProviderDisabled = false))
    assertFalse(getInlineInlays().single().renderer.presentationList.model.disabled)
    provider.hintAdder = {
      addPresentation(InlineInlayPosition(2, true, priority = 1), hasBackground = true) {
        text("1")
      }
    }
    collectAndApplyPass(createPass(providerInfo, isProviderDisabled = true))
    assertTrue(getInlineInlays().single().renderer.presentationList.model.disabled)
  }
}
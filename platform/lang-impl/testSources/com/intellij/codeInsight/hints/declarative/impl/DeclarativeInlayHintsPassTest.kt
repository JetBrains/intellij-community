// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator
import com.intellij.codeInsight.hints.declarative.*
import com.intellij.codeInsight.hints.declarative.impl.inlayRenderer.DeclarativeIndentedBlockInlayRenderer
import com.intellij.codeInsight.hints.declarative.impl.inlayRenderer.DeclarativeInlayRendererBase
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase
import junit.framework.TestCase
import org.junit.Test

class DeclarativeInlayHintsPassTest : LightPlatformCodeInsightFixture4TestCase() {
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
    val pass = createPass(passInfo, false)

    collectAndApplyPass(pass)

    assertEquals(listOf(TextInlayPresentationEntry("inlay text", clickArea = null)), getInlays().single().getEntries())
  }

  @Test
  fun testAddAndUpdateAndRemoveAboveLineInlay() {
    myFixture.configureByText("test.txt", "my content of file")
    var inlayText = "first"
    var inlayAdded = false
    val passInfo = InlayProviderPassInfo(object : InlayHintsProvider {
      override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector? {
        return object : SharedBypassCollector {
          override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
            if (!inlayAdded) {
              inlayAdded = true
              sink.addPresentation(AboveLineIndentedPosition(0), hintFormat = HintFormat.default) {
                text(inlayText)
              }
            }
          }
        }
      }
    }, "test.inlay.provider", emptyMap())
    fun runPass(passInfo: InlayProviderPassInfo, block: (List<Inlay<out DeclarativeIndentedBlockInlayRenderer>>) -> Unit) {
      val pass = createPass(passInfo, false)
      collectAndApplyPass(pass)
      block(getBlockInlays())
    }

    // add inlay
    runPass(passInfo) { inlays ->
      assertSize(1, inlays)
      val inlay = inlays.single()
      assertEquals(
        listOf(TextInlayPresentationEntry("first", clickArea = null)),
        inlay.getEntries()
      )
    }

    // update inlay
    inlayText = "second"
    inlayAdded = false
    runPass(passInfo) { inlays ->
      assertSize(1, inlays)
      val inlay = inlays.single()
      assertEquals(
        listOf(TextInlayPresentationEntry("second", clickArea = null)),
        inlay.getEntries()
      )
    }

    // remove inlay
    runPass(passInfo) { inlays ->
      assertSize(0, inlays)
    }
  }

  private fun Inlay<out DeclarativeInlayRendererBase>.getEntries(): List<InlayPresentationEntry> {
    val presentationList = renderer.presentationList
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

    collectAndApplyPass(createPass(providerInfo, false))

    assertEquals(listOf(TextInlayPresentationEntry("inlay text", clickArea = null)), getInlays().single().getEntries())
    assertNull(provider.hintAdder) // make sure no inlay is added on the next pass

    collectAndApplyPass(createPass(providerInfo, false))
    assertEquals(emptyList<Any>(), getInlays())
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

    collectAndApplyPass(createPass(providerInfo, false))

    val inlay = getInlays().single()
    assertEquals(listOf(TextInlayPresentationEntry("inlay text", clickArea = null)), inlay.getEntries())
    provider.hintAdder = {
      addPresentation(InlineInlayPosition(2, true), hasBackground = true) {
        text("new text")
      }
    }

    collectAndApplyPass(createPass(providerInfo, false))
    val newInlay = getInlays().single()
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

    collectAndApplyPass(createPass(providerInfo, false))

    val entries = getInlays().map { it.getEntries().single() }
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
      collectAndApplyPass(createPass(providerInfo, false))
      assertEquals(tooltip, getInlays().single().renderer.presentationList.model.tooltip)
    }

    addAndCheckInlayWithTooltip("1")
    // the hint is inserted at the same offset, and so the underlying editor inlay should be reused, and it's renderer updated
    addAndCheckInlayWithTooltip("2")
  }

  private fun createPass(providerInfo: InlayProviderPassInfo, isProviderDisabled: Boolean = false): DeclarativeInlayHintsPass {
    return ActionUtil.underModalProgress(project, "") {
      DeclarativeInlayHintsPass(myFixture.file, myFixture.editor, listOf(providerInfo), isProviderDisabled, isProviderDisabled)
    }
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

    collectAndApplyPass(createPass(providerInfo, false))
    assertFalse(getInlays().single().renderer.presentationList.model.disabled)
    provider.hintAdder = {
      addPresentation(InlineInlayPosition(2, true, priority = 1), hasBackground = true) {
        text("1")
      }
    }
    collectAndApplyPass(createPass(providerInfo, true))
    assertTrue(getInlays().single().renderer.presentationList.model.disabled)
  }

  private fun getInlays(): List<Inlay<out DeclarativeInlayRenderer>> {
    val editor = myFixture.editor
    return editor.inlayModel.getInlineElementsInRange(0, editor.document.textLength - 1, DeclarativeInlayRenderer::class.java)
  }

  private fun getBlockInlays(): List<Inlay<out DeclarativeIndentedBlockInlayRenderer>> {
    val editor = myFixture.editor
    return editor.inlayModel.getBlockElementsInRange(0, editor.document.textLength - 1, DeclarativeIndentedBlockInlayRenderer::class.java)
  }

  private fun collectAndApplyPass(pass: DeclarativeInlayHintsPass) {
    pass.doCollectInformation(DaemonProgressIndicator())
    pass.applyInformationToEditor()
  }

  class StoredHintsProvider : InlayHintsProvider {
    var hintAdder: (InlayTreeSink.() -> Unit)? = null
    override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector {
      return object : SharedBypassCollector {
        override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
          val adder = hintAdder
          if (adder != null) {
            hintAdder = null
            adder(sink)
          }
        }
      }
    }
  }
}
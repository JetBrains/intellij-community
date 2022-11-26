// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator
import com.intellij.codeInsight.hints.declarative.*
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

      override fun createCollectorForPreview(file: PsiFile, editor: Editor): InlayHintsCollector? = null
    }, "test.inlay.provider", emptyMap())
    val pass = DeclarativeInlayHintsPass(myFixture.file, myFixture.editor, listOf(passInfo), false)

    collectAndApplyPass(pass)

    assertEquals(listOf(TextInlayPresentationEntry("inlay text", clickArea = null)), getInlays().single().getEntries())
  }

  private fun Inlay<out DeclarativeInlayRenderer>.getEntries(): List<InlayPresentationEntry> {
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

    collectAndApplyPass(DeclarativeInlayHintsPass(myFixture.file, myFixture.editor, listOf(providerInfo), false))

    assertEquals(listOf(TextInlayPresentationEntry("inlay text", clickArea = null)), getInlays().single().getEntries())
    assertNull(provider.hintAdder) // make sure no inlay is added on the next pass

    collectAndApplyPass(DeclarativeInlayHintsPass(myFixture.file, myFixture.editor, listOf(providerInfo), false))
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

    collectAndApplyPass(DeclarativeInlayHintsPass(myFixture.file, myFixture.editor, listOf(providerInfo), false))

    val inlay = getInlays().single()
    assertEquals(listOf(TextInlayPresentationEntry("inlay text", clickArea = null)), inlay.getEntries())
    provider.hintAdder = {
      addPresentation(InlineInlayPosition(2, true), hasBackground = true) {
        text("new text")
      }
    }

    collectAndApplyPass(DeclarativeInlayHintsPass(myFixture.file, myFixture.editor, listOf(providerInfo), false))
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

    collectAndApplyPass(DeclarativeInlayHintsPass(myFixture.file, myFixture.editor, listOf(providerInfo), false))

    val entries = getInlays().map { it.getEntries().single() }
    assertEquals(listOf(
      TextInlayPresentationEntry("2", clickArea = null),
      TextInlayPresentationEntry("4", clickArea = null),
      TextInlayPresentationEntry("1", clickArea = null),
      TextInlayPresentationEntry("3", clickArea = null),
    ), entries)
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

    collectAndApplyPass(DeclarativeInlayHintsPass(myFixture.file, myFixture.editor, listOf(providerInfo), false, false))
    assertFalse(getInlays().single().renderer.presentationList.isDisabled)
    provider.hintAdder = {
      addPresentation(InlineInlayPosition(2, true, priority = 1), hasBackground = true) {
        text("1")
      }
    }
    collectAndApplyPass(DeclarativeInlayHintsPass(myFixture.file, myFixture.editor, listOf(providerInfo), false, true))
    assertTrue(getInlays().single().renderer.presentationList.isDisabled)
  }

  private fun getInlays(): List<Inlay<out DeclarativeInlayRenderer>> {
    val editor = myFixture.editor
    return editor.inlayModel.getInlineElementsInRange(0, editor.document.textLength - 1, DeclarativeInlayRenderer::class.java)
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

    override fun createCollectorForPreview(file: PsiFile, editor: Editor): InlayHintsCollector? = null
  }
}
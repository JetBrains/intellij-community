// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.hints.declarative.*
import org.junit.Test

class DeclarativeInlayMultipleHintsPassTest : DeclarativeInlayHintPassTestBase() {

  @Test
  fun `multiple hints on the same line are sorted by priority`() {
    myFixture.configureByText("test.txt", "my content of file")
    val provider = StoredHintsProvider()
    val passInfo = InlayProviderPassInfo(provider, "test.inlay.provider", emptyMap())
    provider.hintAdder = {
      addPresentation(AboveLineIndentedPosition(0, 0, 1), hintFormat = HintFormat.default) {
        text("1")
      }
      addPresentation(AboveLineIndentedPosition(0, 0, 2), hintFormat = HintFormat.default) {
        text("2")
      }
    }
    runPass(passInfo)
    val inlays = getBlockInlays()
    assertSize(1, inlays)
    assertEquals("2|1", inlays.first().toText())
  }

  @Test
  fun `multiple hints from different providers are on different lines`() {
    myFixture.configureByText("test.txt", "my content of file")
    val provider1 = StoredHintsProvider()
    val passInfo1 = InlayProviderPassInfo(provider1, "test.inlay.provider1", emptyMap())
    provider1.hintAdder = {
      addPresentation(AboveLineIndentedPosition(0, 1, 0), hintFormat = HintFormat.default) {
        text("1")
      }
    }
    val provider2 = StoredHintsProvider()
    val passInfo2 = InlayProviderPassInfo(provider2, "test.inlay.provider2", emptyMap())
    provider2.hintAdder = {
      addPresentation(AboveLineIndentedPosition(0, 1, 0), hintFormat = HintFormat.default) {
        text("2")
      }
    }

    runPass(passInfo1, passInfo2)
    // vertical priorities are equal ⇒ their order is undefined
    val inlays = getBlockInlays().toSet()
    val hintTexts = inlays.map { it.toText() }.toSet()
    assertEquals(setOf("1", "2"), hintTexts)
  }

  @Test
  fun `multiple hints from same provider with different vertical priorities are on different lines`() {
    myFixture.configureByText("test.txt", "my content of file")
    val provider = StoredHintsProvider()
    val passInfo = InlayProviderPassInfo(provider, "test.inlay.provider", emptyMap())
    provider.hintAdder = {
      addPresentation(AboveLineIndentedPosition(0, 1, 0), hintFormat = HintFormat.default) {
        text("1")
      }
      addPresentation(AboveLineIndentedPosition(0, 2, 0), hintFormat = HintFormat.default) {
        text("2")
      }
    }

    runPass(passInfo)
    val inlays = getBlockInlays().sortedByDescending { it.properties.priority }
    val hintTexts = inlays.map { it.toText() }
    assertEquals(listOf("2", "1"), hintTexts)
  }

  @Test
  fun `multiple hints on the same line preserve insertion order`() {
    myFixture.configureByText("test.txt", "my content of file")
    val provider = StoredHintsProvider()
    val passInfo = InlayProviderPassInfo(provider, "test.inlay.provider", emptyMap())
    provider.hintAdder = {
      addPresentation(AboveLineIndentedPosition(0, 0, 0), hintFormat = HintFormat.default) {
        text("1")
      }
      addPresentation(AboveLineIndentedPosition(0, 1, 0), hintFormat = HintFormat.default) {
        text("irrelevant")
      }
      addPresentation(AboveLineIndentedPosition(0, 0, 0), hintFormat = HintFormat.default) {
        text("2")
      }
    }

    runPass(passInfo)
    val hintTexts = getBlockInlays().sortedByDescending { it.properties.priority }.map { it.toText() }
    assertEquals("1|2", hintTexts.last())
  }

  @Test
  fun `block inlay is reused and multiple hints preserve collapse state on subsequent passes`() {
    myFixture.configureByText("test.txt", "my content of file")
    val provider = StoredHintsProvider()
    val passInfo = InlayProviderPassInfo(provider, "test.inlay.provider", emptyMap())
    val adder: (InlayTreeSink.() -> Unit) = {
      addPresentation(AboveLineIndentedPosition(0, 0, 0), hintFormat = HintFormat.default) {
        collapsibleList(
          state = CollapseState.Collapsed,
          collapsedState = { toggleButton { text("one") } },
          expandedState = { text("1") }
        )
      }
      addPresentation(AboveLineIndentedPosition(0, 0, 0), hintFormat = HintFormat.default) {
        collapsibleList(
          state = CollapseState.Collapsed,
          collapsedState = { toggleButton { text("two") } },
          expandedState = { text("2") }
        )
      }
    }

    provider.hintAdder = adder
    runPass(passInfo)
    val inlays1 = getBlockInlays()
    assertEquals(listOf("one|two"), inlays1.map { it.toText() })
    // expand hints
    inlays1.flatMap { it.renderer.presentationLists }.forEach { it.getEntries().single().simulateClick(myFixture.editor, it) }
    assertEquals(listOf("1|2"), inlays1.map { it.toText() })

    provider.hintAdder = adder
    runPass(passInfo)
    val inlays2 = getBlockInlays()
    assertEquals(listOf("1|2"), inlays2.map { it.toText() })
    assertSame(inlays1.first(), inlays2.first())
  }

  @Test
  fun `inlay with multiple hints is updated when the number of hints changes`() {
    myFixture.configureByText("test.txt", "my content of file")
    val provider = StoredHintsProvider()
    val passInfo = InlayProviderPassInfo(provider, "test.inlay.provider", emptyMap())
    var text1 = "1"
    var text2: String? = "2"
    val adder: (InlayTreeSink.() -> Unit) = {
      addPresentation(AboveLineIndentedPosition(0, 0, 1), hintFormat = HintFormat.default) {
        collapsibleList(
          state = CollapseState.Collapsed,
          collapsedState = { toggleButton { text("one") } },
          expandedState = { text(text1) }
        )
      }
      if (text2 != null) {
        addPresentation(AboveLineIndentedPosition(0, 0, 2), hintFormat = HintFormat.default) {
          collapsibleList(
            state = CollapseState.Collapsed,
            collapsedState = { toggleButton { text("two") } },
            expandedState = { text(text2!!) }
          )
        }
      }
    }

    provider.hintAdder = adder
    runPass(passInfo)
    val inlays1 = getBlockInlays()
    assertEquals(listOf("two|one"), inlays1.map { it.toText() })
    // expand hints
    inlays1.flatMap { it.renderer.presentationLists }.first().let { it.getEntries().single().simulateClick(myFixture.editor, it) }
    assertEquals(listOf("2|one"), inlays1.map { it.toText() })

    text2 = null
    provider.hintAdder = adder
    runPass(passInfo)
    val inlays2 = getBlockInlays()
    assertEquals(listOf("one"), inlays2.map { it.toText() })
    assertSame(inlays1.first(), inlays2.first())
  }

  @Test
  fun `inlay is deleted if all hints are deleted`() {
    myFixture.configureByText("test.txt", "my content of file")
    val provider = StoredHintsProvider()
    val passInfo = InlayProviderPassInfo(provider, "test.inlay.provider", emptyMap())
    provider.hintAdder = {
      addPresentation(AboveLineIndentedPosition(0, 0, 0), hintFormat = HintFormat.default) {
        text("1")
      }
      addPresentation(AboveLineIndentedPosition(0, 0, 0), hintFormat = HintFormat.default) {
        text("2")
      }
    }

    runPass(passInfo)
    val inlays = getBlockInlays().sortedByDescending { it.properties.priority }
    val hintTexts = inlays.map { it.toText() }
    assertEquals(listOf("1|2"), hintTexts)

    provider.hintAdder = null
    runPass(passInfo)
    assertEmpty(getBlockInlays())
  }
}
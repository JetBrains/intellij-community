// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.hints.declarative.AboveLineIndentedPosition
import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.InlayProviderPassInfo
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.impl.util.DeclarativeHintsDumpUtil
import com.intellij.codeInsight.hints.declarative.impl.views.TextInlayPresentationEntry
import com.intellij.testFramework.requireIs
import org.junit.Test

class DumpDeclarativeHintsTest : DeclarativeInlayHintPassTestBase() {
  @Test
  fun `generated dump can be extracted`() {
    val source = "my content of file"
    myFixture.configureByText("test.txt", source)
    val provider = StoredHintsProvider()
    val passInfo = InlayProviderPassInfo(provider, "test.inlay.provider", emptyMap())
    provider.hintAdder = {
      addPresentation(AboveLineIndentedPosition(0, 0), hintFormat = HintFormat.default) {
        text("1")
      }
      addPresentation(AboveLineIndentedPosition(0, 0), hintFormat = HintFormat.default) {
        text("2")
      }
      addPresentation(InlineInlayPosition(2, true), hintFormat = HintFormat.default) {
        text("3")
      }
    }
    runPass(passInfo)
    val dump = DeclarativeHintsDumpUtil.dumpHints(source, myFixture.editor) {
      it.getEntries().joinToString(separator = "") { it as TextInlayPresentationEntry; it.text }
    }
    val expectedDump = """
      /*<# block [1] [2] #>*/
      my/*<# 3 #>*/ content of file
    """.trimIndent()
    assertEquals(expectedDump, dump)
    val extracted = DeclarativeHintsDumpUtil.extractHints(dump)
    assertSize(3, extracted)
    val pos1 = extracted[0].run {
      assertEquals("1", text)
      val pos = position.requireIs<AboveLineIndentedPosition>()
      pos.offset in source.indices
      pos
    }
    val pos2 = extracted[1].run {
      assertEquals("2", text)
      val pos = position.requireIs<AboveLineIndentedPosition>()
      pos.offset in source.indices
      pos
    }
    assertEquals(pos1.verticalPriority, pos2.verticalPriority)
    extracted[2].run {
      assertEquals("3", text)
      val pos = position.requireIs<InlineInlayPosition>()
      assertEquals(2, pos.offset)
    }
  }
}
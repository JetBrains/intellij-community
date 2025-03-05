// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.hints.declarative.AboveLineIndentedPosition
import com.intellij.codeInsight.hints.declarative.HintColorKind
import com.intellij.codeInsight.hints.declarative.HintFontSize
import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.HintMarginPadding
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.impl.util.DeclarativeHintsDumpUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ExtractDeclarativeHintsTest {
  @Test
  fun `extract single inline inlay`() {
    val source = """
      123/*<# cont ents #>*/456
    """.trimIndent()
    val extracted = DeclarativeHintsDumpUtil.extractHints(source)
    extracted.single().run {
      val position = position as InlineInlayPosition
      assertEquals(3, position.offset)
      assertEquals("cont ents", text)
      assertEquals(hintFormat, HintFormat.Companion.default)
    }
  }

  @Test
  fun `extract block inlay with multiple hints`() {
    val source = """
      123
        /*<# block [one] [two] #>*/
        456
    """.trimIndent()
    val extracted = DeclarativeHintsDumpUtil.extractHints(source)
    assertEquals(2, extracted.size)
    assertTrue(extracted.all { (it.position as AboveLineIndentedPosition).offset in 4..9 })
    assertEquals("one", extracted[0].text)
    assertEquals("two", extracted[1].text)
    assertTrue(extracted.all { it.hintFormat == HintFormat.Companion.default })
    assertEquals(
      (extracted[0].position as AboveLineIndentedPosition).verticalPriority,
      (extracted[1].position as AboveLineIndentedPosition).verticalPriority
    )
  }

  @Test
  fun `extract hints with formatting directives`() {
    val source = """
      /*<# block fmt:colorKind=TextWithoutBackground,
                    fontSize=ABitSmallerThanInEditor,
                    marginPadding=MarginAndSmallerPadding #>*/
      1234
        /*<# block [foo] #>*/
        4567
    """.trimIndent()
    val extracted = DeclarativeHintsDumpUtil.extractHints(source)
    assertSize(1, extracted)
    extracted.single().run {
      val position = position as AboveLineIndentedPosition
      assertTrue(position.offset in 5..11) { "Hint offset (${position.offset}) is not from the line below" }
      assertEquals("foo", text)
      assertEquals(
        HintFormat.Companion.default
          .withColorKind(HintColorKind.TextWithoutBackground)
          .withFontSize(HintFontSize.ABitSmallerThanInEditor)
          .withHorizontalMargin(HintMarginPadding.MarginAndSmallerPadding),
        hintFormat
      )
    }
  }

  @Test
  fun `extract hints with multiple inplace formatting directives`() {
    val source = """
      123
        /*<# block fmt:colorKind=TextWithoutBackground [one] fmt:colorKind=Parameter [two] #>*/
        456
    """.trimIndent()
    val extracted = DeclarativeHintsDumpUtil.extractHints(source)
    assertSize(2, extracted)
    assertEquals(extracted[0].hintFormat.colorKind, HintColorKind.TextWithoutBackground)
    assertEquals(extracted[0].text, "one")
    assertEquals(extracted[1].hintFormat.colorKind, HintColorKind.Parameter)
    assertEquals(extracted[1].text, "two")
  }

  @Test
  fun `extract inline hint with formatting directives`() {
    val source = """
      123/*<#] fmt:colorKind=TextWithoutBackground [one] [#>*/456
    """.trimIndent()
    val extracted = DeclarativeHintsDumpUtil.extractHints(source)
    assertSize(1, extracted)
    assertEquals(extracted.single().hintFormat.colorKind, HintColorKind.TextWithoutBackground)
    assertEquals(extracted.single().text, "one")
  }

  @Test
  fun `extract inline hint with paired brackets`() {
    val source = """
      123/*<# List[Int] #>*/456
    """.trimIndent()
    val extracted = DeclarativeHintsDumpUtil.extractHints(source)
    assertSize(1, extracted)
    assertEquals(extracted.single().text, "List[Int]")
  }

  @Test
  fun `incorrect inline hint with directives`() {
    assertThrows<DeclarativeHintsDumpUtil.ParserException> {
      print(DeclarativeHintsDumpUtil.extractHints("""/*<#] [one] #>*/"""))
    }
    assertThrows<DeclarativeHintsDumpUtil.ParserException> {
      print(DeclarativeHintsDumpUtil.extractHints("""/*<# ] [one][ #>*/"""))
    }
  }

  @Test
  fun `unpaired bracket`() {
    assertThrows<DeclarativeHintsDumpUtil.ParserException> {
      DeclarativeHintsDumpUtil.extractHints("""/*<# shhh[ #>*/""")
    }
    DeclarativeHintsDumpUtil.extractHints("""/*<# shhh\[ #>*/""").let {
      assertSize(1, it)
      assertEquals("shhh[", it.single().text)
    }
  }

}

private fun assertSize(expected: Int, actual: List<*>) {
  assertEquals(expected, actual.size, "Wrong size: $actual")
}
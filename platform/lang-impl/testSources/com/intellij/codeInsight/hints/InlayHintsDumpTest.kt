// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.hints.InlayDumpUtil.InlayData
import com.intellij.codeInsight.hints.InlayDumpUtil.InlayDumpPlacement
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase
import org.junit.Test

class InlayHintsDumpTest : LightPlatformCodeInsightFixture4TestCase() {
  @Test
  fun `extract inline entries`() {
    assertEquals(
      listOf(InlayData(5, InlayDumpPlacement.Inline, "foo"),
             InlayData(8, InlayDumpPlacement.Inline, "bar")),
      extractInlays("01234/*<# foo #>*/567/*<# bar #>*/")
    )
  }

  @Test
  fun `extract block above entries`() {
    val extracted = extractInlays("""
      |some text
      |    /*<# block content #>*/
      |    some more text
    """.trimMargin())
    assertEquals(listOf(InlayData(10, InlayDumpPlacement.BlockAbove, "content")), extracted)
  }

  @Test
  fun testExtractCombinedEntries() {
    val text = """
      import something
      /*<# block first above-line inlay #>*/
      /*<# block second above-line inlay #>*/
      fun foo() {
        /*<# block third above-line inlay with trailing spaces #>*/   
        val x/*<# inline inlay #>*/ = 1/*<# eol inlay #>*/
      }
    """.trimIndent()
    assertEquals(
      listOf(InlayData(17, InlayDumpPlacement.BlockAbove, "first above-line inlay"),
             InlayData(17, InlayDumpPlacement.BlockAbove, "second above-line inlay"),
             InlayData(29, InlayDumpPlacement.BlockAbove, "third above-line inlay with trailing spaces"),
             InlayData(36, InlayDumpPlacement.Inline, "inline inlay"),
             InlayData(40, InlayDumpPlacement.Inline, "eol inlay")),
      extractInlays(text)
    )

  }

  @Test
  fun `inlay may contain octothorpe`() {
    assertEquals(
      listOf(InlayData(5, InlayDumpPlacement.Inline, "foo#bar")),
      extractInlays("01234/*<# foo#bar #>*/567"))
  }

  @Test
  fun `entire block inlay line is removed`() {
    val text = """
      fun foo() {
        println("indented")
      } 
    """.trimIndent()
    val textWithInlay = """
      fun foo() {
        /*<# block 123 #>*/    
        println("indented")
      } 
    """.trimIndent()
    assertEquals(text, InlayDumpUtil.removeInlays(textWithInlay))
  }

  @Test
  fun `inlay contents may be multiline`() {
    val text = """
      fun foo() {
        /*<# block this is a very
             very very long inlay #>*/
        print("123")
      }
    """.trimIndent()
    val withoutInlay = """
      fun foo() {
        print("123")
      }
    """.trimIndent()
    assertEquals(withoutInlay, InlayDumpUtil.removeInlays(text))
    assertEquals(
      listOf(InlayData(12, InlayDumpPlacement.BlockAbove, "this is a very\n       very very long inlay")),
      extractInlays(text)
    )
  }


  @Test
  fun `indent dumped block inlays`() {
    val text = """
        fun foo() {
            println("Hello")
        }
    """.trimIndent()
    myFixture.configureByText("test.txt", text)

    class DummyInlayRenderer(val text: String) : EditorCustomElementRenderer {
      override fun calcWidthInPixels(inlay: Inlay<*>): Int = 42
    }

    myFixture.editor.inlayModel.addBlockElement(16, true, true, 0, DummyInlayRenderer("foo"))

    fun dumpInlays(indentBlockInlays: Boolean): String =
      InlayDumpUtil.dumpInlays(
        text,
        editor = myFixture.editor,
        renderer = { r, _ -> r as DummyInlayRenderer; r.text },
        indentBlockInlays = indentBlockInlays
      )

    val inlayDump = dumpInlays(indentBlockInlays = false)
    val expected = """
        fun foo() {
        /*<# block foo #>*/
            println("Hello")
        }
    """.trimIndent()
    assertEquals(expected, inlayDump)

    val indentedInlayDump = dumpInlays(indentBlockInlays = true)
    val expectedIndented = """
        fun foo() {
            /*<# block foo #>*/
            println("Hello")
        }
    """.trimIndent()
    assertEquals(expectedIndented, indentedInlayDump)
  }

  private fun extractInlays(text: String): List<InlayData> {
    return InlayDumpUtil.extractInlays(text).map { it.copy(content = it.content.trim()) }
  }


}
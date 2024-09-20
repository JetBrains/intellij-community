// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase
import org.junit.Test

class InlayHintsDumpTest : LightPlatformCodeInsightFixture4TestCase() {
  @Test
  fun testExtractEntries() {
    assertEquals(listOf(5 to "foo", 8 to "bar"), InlayDumpUtil.extractEntries("01234/*<# foo #>*/567/*<# bar #>*/"))
  }

  @Test
  fun `inlay may contain octothorpe`() {
    assertEquals(listOf(5 to "foo#bar"), InlayDumpUtil.extractEntries("01234/*<# foo#bar #>*/567"))
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
    assertEquals(text, InlayDumpUtil.removeHints(textWithInlay))
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
      InlayDumpUtil.dumpHintsInternal(text, editor = myFixture.editor, renderer = { r, _ -> r as DummyInlayRenderer; r.text }, indentBlockInlays = indentBlockInlays)

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


}
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.unit

import com.intellij.platform.lsp.impl.util.LspSnippetParser
import com.intellij.platform.lsp.impl.util.LspSnippetPart.TabStop
import com.intellij.platform.lsp.impl.util.LspSnippetPart.Text
import com.intellij.platform.lsp.impl.util.LspSnippetPart.Variable
import com.intellij.platform.lsp.impl.util.LspSnippetTemplate
import com.intellij.platform.lsp.impl.util.LspSnippetTemplate.Segment
import com.intellij.platform.lsp.impl.util.LspSnippetTemplate.Tab
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** The snippet grammar and the live template it becomes. */
class LspSnippetsTest {
  private val noVariables: (String) -> String? = { null }

  @Test
  fun `tab stops, placeholders, choices and variables parse`() {
    assertEquals(
      listOf(Text("f("), TabStop(1), Text(", "), TabStop(2), Text(")"), TabStop(0)),
      LspSnippetParser.parse("f($1, \${2})$0"),
    )
    assertEquals(listOf(TabStop(1, listOf(Text("name")))), LspSnippetParser.parse("\${1:name}"))
    assertEquals(listOf(TabStop(1, listOf(Text("a")), listOf("a", "b,c", "d|"))), LspSnippetParser.parse("\${1|a,b\\,c,d\\||}"))
    assertEquals(listOf(Variable("TM_FILENAME"), Text(" "), Variable("X", listOf(Text("def"))), Text(" "), Variable("Y")),
                 LspSnippetParser.parse("\$TM_FILENAME \${X:def} \${Y}"))
  }

  @Test
  fun `placeholders nest and escapes are literal`() {
    assertEquals(listOf(TabStop(1, listOf(Text("foo("), TabStop(2, listOf(Text("bar"))), Text(")")))), LspSnippetParser.parse("\${1:foo(\${2:bar})}"))
    assertEquals(listOf(Text("a\$b}c\\d")), LspSnippetParser.parse("a\\\$b\\}c\\\\d"))
    assertEquals(listOf(Text("\\n")), LspSnippetParser.parse("\\n")) // not an escape
  }

  @Test
  fun `a construct that does not parse is text, a transform is ignored`() {
    assertEquals(listOf(Text("\${1:open")), LspSnippetParser.parse("\${1:open"))
    assertEquals(listOf(Text("cost $ 5 \${")), LspSnippetParser.parse("cost $ 5 \${"))
    assertEquals(listOf(TabStop(1), Text(" "), Variable("TM_FILENAME")), LspSnippetParser.parse("\${1/(.*)/\${1:/upcase}/g} \${TM_FILENAME/a\\/b/c/}"))
    assertEquals(listOf(Text("}")), LspSnippetParser.parse("}"))
  }

  @Test
  fun `tab stops go in index order, a repeat mirrors, $0 is the end`() {
    val template = LspSnippetTemplate.of(LspSnippetParser.parse("\${2:b} \${1:a} \$2 \$0;"), noVariables)
    assertEquals(
      listOf(Segment.Stop("TAB_2"), Segment.Text(" "), Segment.Stop("TAB_1"), Segment.Text(" "), Segment.Stop("TAB_2"), Segment.Text(" "), Segment.End, Segment.Text(";")),
      template.segments,
    )
    assertEquals(listOf(Tab("TAB_1", "a", null), Tab("TAB_2", "b", null)), template.variables)
    assertNull(template.plainText())
  }

  @Test
  fun `the end goes last without $0, nested placeholders flatten, choices keep their values`() {
    val template = LspSnippetTemplate.of(LspSnippetParser.parse("\${1:foo(\${2:bar})} \${3|x,y|}"), noVariables)
    assertEquals(listOf(Segment.Stop("TAB_1"), Segment.Text(" "), Segment.Stop("TAB_3"), Segment.End), template.segments)
    assertEquals(listOf(Tab("TAB_1", "foo(bar)", null), Tab("TAB_3", "x", listOf("x", "y"))), template.variables)
  }

  @Test
  fun `variables resolve, an empty one takes its default, an unknown one is a placeholder after the tab stops`() {
    val resolve: (String) -> String? = { mapOf("TM_FILENAME" to "a.kt", "TM_SELECTED_TEXT" to "")[it] }
    val template = LspSnippetTemplate.of(LspSnippetParser.parse("\$TM_FILENAME \${TM_SELECTED_TEXT:\${1:sel}} \$UNKNOWN \$1"), resolve)
    assertEquals(
      listOf(Segment.Text("a.kt "), Segment.Stop("TAB_1"), Segment.Text(" "), Segment.Stop("VAR_UNKNOWN"), Segment.Text(" "), Segment.Stop("TAB_1"), Segment.End),
      template.segments,
    )
    assertEquals(listOf(Tab("TAB_1", "sel", null), Tab("VAR_UNKNOWN", "UNKNOWN", null)), template.variables)
  }

  @Test
  fun `a snippet without tab stops is plain text with the caret at $0`() {
    assertEquals("foo()" to 4, LspSnippetTemplate.of(LspSnippetParser.parse("foo(\$0)"), noVariables).plainText())
    assertEquals("a\$b" to 3, LspSnippetTemplate.of(LspSnippetParser.parse("a\\\$b"), noVariables).plainText())
    assertEquals("x" to 1, LspSnippetTemplate.of(LspSnippetParser.parse("\${0:x}"), noVariables).plainText())
  }

  @Test
  fun `an unclosed placeholder is its opening text and its content`() {
    assertEquals(listOf(Text("\${1:a "), TabStop(2, listOf(Text("b"))), Text(" \${3:c")), LspSnippetParser.parse("\${1:a \${2:b} \${3:c"))
    val unclosed = "\${1:".repeat(10_000)
    assertEquals(listOf(Text(unclosed)), LspSnippetParser.parse(unclosed))
  }

  @Test
  fun `a format entry of a transform may contain a slash`() {
    assertEquals(listOf(TabStop(1), Text("!")), LspSnippetParser.parse("\${1/(a)/\${1:+x/y}/}!"))
  }

  @Test
  fun `an empty variable in a placeholder takes its default or nothing`() {
    val template = LspSnippetTemplate.of(LspSnippetParser.parse("\${1:\$TM_FILENAME} \${2:\${TM_FILENAME:dflt}}")) { "" }
    assertEquals(listOf(Tab("TAB_1", "", null), Tab("TAB_2", "dflt", null)), template.variables)
  }
}

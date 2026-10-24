// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.DocumentText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Tests the contract of [DocumentText].
 */
internal class DocumentTextTest {

  @Test
  fun `each line separator ends a line`() {
    val text = DocumentText.createText("a\nb\rc\r\nd")
    assertEquals(4, text.lineCount())
    assertEquals(listOf(0, 2, 4, 7), (0 until 4).map { text.lineStartOffset(it) })
    assertEquals(listOf(1, 3, 5, 8), (0 until 4).map { text.lineEndOffset(it) })
    assertEquals(listOf(1, 1, 2, 0), (0 until 4).map { text.lineSeparatorLength(it) })
  }

  @Test
  fun `a separator belongs to the line that it ends`() {
    val text = DocumentText.createText("a\nb\rc\r\nd")
    assertEquals(0, text.lineNumber(1))
    assertEquals(1, text.lineNumber(3))
    assertEquals(2, text.lineNumber(5))
    assertEquals(2, text.lineNumber(6))
    assertEquals(3, text.lineNumber(7))
    assertEquals(3, text.lineNumber(8))
  }

  @Test
  fun `a text that ends with a separator ends with an empty line`() {
    val text = DocumentText.createText("a\n")
    assertEquals(2, text.lineCount())
    assertEquals(2, text.lineStartOffset(1))
    assertEquals(2, text.lineEndOffset(1))
    assertEquals(0, text.lineSeparatorLength(1))
    assertEquals(1, text.lineNumber(2))
  }

  @Test
  fun `the empty text has no lines, but line 0 is at offset 0`() {
    val text = DocumentText.createText("")
    assertEquals(0, text.lineCount())
    assertEquals(0, text.lineStartOffset(0))
    assertEquals(0, text.lineEndOffset(0))
    assertEquals(0, text.lineSeparatorLength(0))
    assertEquals(0, text.lineNumber(0))
  }

  @Test
  fun `an offset or a line outside the text fails`() {
    val text = DocumentText.createText("ab")
    assertThrows(IndexOutOfBoundsException::class.java) { text.lineNumber(-1) }
    assertThrows(IndexOutOfBoundsException::class.java) { text.lineNumber(3) }
    assertThrows(IndexOutOfBoundsException::class.java) { text.lineStartOffset(-1) }
    assertThrows(IndexOutOfBoundsException::class.java) { text.lineStartOffset(1) }
    assertThrows(IndexOutOfBoundsException::class.java) { text.lineEndOffset(1) }
    assertThrows(IndexOutOfBoundsException::class.java) { text.lineSeparatorLength(1) }
    assertThrows(IndexOutOfBoundsException::class.java) { text.string(-1, 1) }
    assertThrows(IndexOutOfBoundsException::class.java) { text.string(1, 3) }
  }

  @Test
  fun `string returns the characters in the range`() {
    val text = DocumentText.createText("abcd")
    assertEquals("bc", text.string(1, 3))
    assertEquals("", text.string(2, 2))
    assertEquals("abcd", text.string(0, 4))
  }

  @Test
  fun `luckyChars returns the same characters as chars`() {
    val fromString = DocumentText.createText("ab")
    val edited = DocumentText.createText(StringBuilder("ab")).insert(1, "x")
    for (text in listOf(fromString, edited)) {
      assertEquals(text.chars().toString(), text.luckyChars().toString())
    }
  }

  @Test
  fun `a later change of the input does not change the text`() {
    val chars = StringBuilder("ab")
    val fragment = StringBuilder("x")
    val text = DocumentText.createText(chars)
    val inserted = text.insert(1, fragment)
    val replaced = text.replace(0, 1, fragment)
    chars.append("c")
    fragment.append("y")
    assertEquals("ab", text.string())
    assertEquals("axb", inserted.string())
    assertEquals("xb", replaced.string())
  }

  @Test
  fun `texts compare by identity`() {
    assertNotEquals(DocumentText.createText("ab"), DocumentText.createText("ab"))
  }

  @Test
  fun `an empty edit returns the same text`() {
    val text = DocumentText.createText("ab")
    for (offset in 0..text.length()) {
      assertSame(text, text.insert(offset, "")) { "insert at $offset" }
      assertSame(text, text.delete(offset, offset)) { "delete at $offset" }
      assertSame(text, text.replace(offset, offset, "")) { "replace at $offset" }
    }
  }

  @Test
  fun `an empty edit outside the text fails`() {
    val text = DocumentText.createText("ab")
    for (offset in listOf(-1, 3)) {
      assertThrows(IndexOutOfBoundsException::class.java) { text.insert(offset, "") }
      assertThrows(IndexOutOfBoundsException::class.java) { text.delete(offset, offset) }
      assertThrows(IndexOutOfBoundsException::class.java) { text.replace(offset, offset, "") }
    }
  }

  @Test
  fun `an edit outside the text fails`() {
    val text = DocumentText.createText("ab")
    assertThrows(IndexOutOfBoundsException::class.java) { text.insert(-1, "x") }
    assertThrows(IndexOutOfBoundsException::class.java) { text.insert(3, "x") }
    assertThrows(IndexOutOfBoundsException::class.java) { text.delete(-1, 1) }
    assertThrows(IndexOutOfBoundsException::class.java) { text.delete(1, 3) }
    assertThrows(IndexOutOfBoundsException::class.java) { text.replace(1, 3, "x") }
    assertThrows(IndexOutOfBoundsException::class.java) { DocumentText.createText("").insert(1, "x") }
  }

  @Test
  fun `a reversed range fails`() {
    val text = DocumentText.createText("abc")
    assertThrows(IndexOutOfBoundsException::class.java) { text.string(2, 1) }
    assertThrows(IndexOutOfBoundsException::class.java) { text.delete(2, 1) }
    assertThrows(IndexOutOfBoundsException::class.java) { text.replace(2, 1, "x") }
  }
}

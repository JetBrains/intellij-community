// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.fragmented

import com.intellij.diff.tools.fragmented.LineColumnTransfer.transferColumn
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LineColumnTransferTest {
  @Test
  fun `an equal line keeps the column`() {
    for (column in 0..7) {
      assertEquals(column, transferColumn("my text", "my text", column), "column $column")
    }
    assertNull(transferColumn("my text", "my text", 8), "a column past the line end")
  }

  @Test
  fun `an indent is removed`() {
    val from = "   my text"
    val to = "my     text"
    assertEquals(0, transferColumn(from, to, 0))
    assertEquals(0, transferColumn(from, to, 1))
    assertEquals(0, transferColumn(from, to, 3))
    assertEquals(2, transferColumn(from, to, 5), "right after 'my'")
    assertEquals(7, transferColumn(from, to, 6), "right before 'text'")
    assertEquals(9, transferColumn(from, to, 8), "inside 'text'")
    assertEquals(11, transferColumn(from, to, 10), "the end of the line")
    assertNull(transferColumn(from, to, 11), "a column past the line end")
  }

  @Test
  fun `an indent is added`() {
    val from = "my     text"
    val to = "   my text"
    assertEquals(3, transferColumn(from, to, 0), "right before 'my'")
    assertEquals(5, transferColumn(from, to, 2), "right after 'my'")
    assertEquals(6, transferColumn(from, to, 4), "inside the blank run, clamped to its end")
    assertEquals(6, transferColumn(from, to, 7), "right before 'text'")
    assertEquals(10, transferColumn(from, to, 11), "the end of the line")
  }

  @Test
  fun `trailing blanks are clamped`() {
    assertEquals(2, transferColumn("my  ", "my", 4))
    assertNull(transferColumn("my  ", "my", 5), "a column past the line end")
  }

  @Test
  fun `a column past the line end is not transferred`() {
    assertNull(transferColumn("my   ", "my", 9))
    assertEquals(0, transferColumn("", "   ", 0))
    assertNull(transferColumn("", "   ", 1))
  }

  @Test
  fun `a surrogate pair and a CJK word count as code units`() {
    val from = "  \u4F60\u597D \uD83D\uDE00 x" // two spaces, ni hao, a space, a grinning face, a space, x
    val to = "\u4F60\u597D \uD83D\uDE00  x"
    assertEquals(0, transferColumn(from, to, 2), "right before the CJK word")
    assertEquals(2, transferColumn(from, to, 4), "right after the CJK word")
    assertEquals(3, transferColumn(from, to, 5), "right before the emoji")
    assertEquals(5, transferColumn(from, to, 7), "right after the emoji")
    assertEquals(7, transferColumn(from, to, 8), "right before 'x'")
    assertEquals(8, transferColumn(from, to, 9), "the end of the line")
    assertEquals(4, transferColumn(from, to, 6), "between the halves of the surrogate pair, which stays safe")
  }

  @Test
  fun `an ideographic space is not blank, as in the comparison`() {
    assertNull(transferColumn("a\u3000b", "a b", 2))
  }

  @Test
  fun `a line with another difference is not transferred`() {
    assertNull(transferColumn("ab", "a", 2))
    assertNull(transferColumn("my text", "my test", 0))
    assertNull(transferColumn("a", "a", -1), "a negative column")
  }
}

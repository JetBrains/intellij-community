// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.experimental

import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.ex.experimental.Agent
import com.intellij.openapi.editor.ex.experimental.DocumentBranch
import com.intellij.openapi.editor.ex.experimental.LineageMismatchException
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Tests [GraphBase]: the lineage, and the characters that a new branch shares with its base text.
 */
internal class GraphBaseTest {

  @Test
  fun `the base text of a new branch shares the characters of the branch text`() {
    val branch = DocumentBranch.createBranch("abc", Agent.createAgent("a"))
    val graph = EventGraphImpl.implOf(branch.graph())
    assertSame(branch.text().chars(), graph.baseText().chars())
    // The base text is a value of its own, so it keeps none of the line data of the branch text.
    assertNotSame(branch.text(), graph.baseText())
  }

  @Test
  fun `each base starts a lineage of its own`() {
    val text = DocumentText.createText("abc")
    val first = GraphBase.create(text)
    val second = GraphBase.create(text)
    first.assertSameLineage(first)
    assertThrows(LineageMismatchException::class.java) {
      first.assertSameLineage(second)
    }
  }
}

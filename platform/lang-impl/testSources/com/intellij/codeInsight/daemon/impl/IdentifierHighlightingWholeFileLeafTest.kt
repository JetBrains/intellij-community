// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.CodeInsightTestUtil

/**
 * A file with no fine-grained PSI has one leaf which spans the whole file.
 * [IdentifierHighlightingComputer] must not use that leaf as a cache target,
 * because the check then costs one symbol resolution per character.
 */
class IdentifierHighlightingWholeFileLeafTest : BasePlatformTestCase() {
  fun testWholeFileLeafIsNotACacheTarget() {
    myFixture.configureByText("test.txt", "aaa b<caret>bb aaa")
    val psiFile = myFixture.file
    val caretOffset = myFixture.caretOffset
    val leaf = psiFile.findElementAt(caretOffset)
    assertNotNull("the test needs a leaf under the caret", leaf)
    assertEquals("the test needs a whole-file leaf", psiFile.textRange, leaf!!.textRange)

    val result = CodeInsightTestUtil.runIdentifierHighlighterPass(psiFile, myFixture.editor)
    assertEquals(listOf(TextRange(caretOffset, caretOffset)), result.targets.map { TextRange.create(it) })
  }
}

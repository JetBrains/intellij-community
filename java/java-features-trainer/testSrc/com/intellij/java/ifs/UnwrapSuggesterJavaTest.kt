// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ifs

import junit.framework.TestCase
import training.featuresSuggester.FeatureSuggesterTestUtils.deleteSymbolAtCaret
import training.featuresSuggester.FeatureSuggesterTestUtils.insertNewLineAt
import training.featuresSuggester.FeatureSuggesterTestUtils.moveCaretToLogicalPosition
import training.featuresSuggester.FeatureSuggesterTestUtils.selectBetweenLogicalPositions
import training.featuresSuggester.FeatureSuggesterTestUtils.testInvokeLater
import training.featuresSuggester.FeatureSuggesterTestUtils.typeAndCommit
import training.featuresSuggester.NoSuggestion
import training.featuresSuggester.UnwrapSuggesterTest

class UnwrapSuggesterJavaTest : UnwrapSuggesterTest() {
  override val testingCodeFileName = "JavaCodeExample.java"

  override fun getTestDataPath() = JavaSuggestersTestUtils.testDataPath

  override fun `testUnwrap IF statement and get suggestion`() {
    with(myFixture) {
      moveCaretToLogicalPosition(11, 9)
      deleteSymbolAtCaret()
      selectBetweenLogicalPositions(lineStartIndex = 9, columnStartIndex = 41, lineEndIndex = 9, columnEndIndex = 3)
      deleteSymbolAtCaret()
    }

    testInvokeLater(project) {
      assertSuggestedCorrectly()
    }
  }

  override fun `testUnwrap one-line IF and get suggestion`() {
    with(myFixture) {
      selectBetweenLogicalPositions(
        lineStartIndex = 41,
        columnStartIndex = 25,
        lineEndIndex = 41,
        columnEndIndex = 12
      )
      deleteSymbolAtCaret()
      moveCaretToLogicalPosition(41, 18)
      deleteSymbolAtCaret()
    }

    testInvokeLater(project) {
      assertSuggestedCorrectly()
    }
  }

  override fun `testUnwrap IF with deleting multiline selection and get suggestion`() {
    with(myFixture) {
      selectBetweenLogicalPositions(lineStartIndex = 8, columnStartIndex = 23, lineEndIndex = 10, columnEndIndex = 5)
      deleteSymbolAtCaret()
      moveCaretToLogicalPosition(9, 9)
      deleteSymbolAtCaret()
    }

    testInvokeLater(project) {
      assertSuggestedCorrectly()
    }
  }

  override fun `testUnwrap FOR and get suggestion`() {
    with(myFixture) {
      selectBetweenLogicalPositions(lineStartIndex = 32, columnStartIndex = 40, lineEndIndex = 32, columnEndIndex = 9)
      deleteSymbolAtCaret()
      moveCaretToLogicalPosition(35, 13)
      deleteSymbolAtCaret()
    }

    testInvokeLater(project) {
      assertSuggestedCorrectly()
    }
  }

  override fun `testUnwrap WHILE and get suggestion`() {
    with(myFixture) {
      selectBetweenLogicalPositions(lineStartIndex = 37, columnStartIndex = 26, lineEndIndex = 37, columnEndIndex = 0)
      deleteSymbolAtCaret()
      moveCaretToLogicalPosition(40, 13)
      deleteSymbolAtCaret()
    }

    testInvokeLater(project) {
      assertSuggestedCorrectly()
    }
  }

  override fun `testUnwrap commented IF and don't get suggestion`() {
    with(myFixture) {
      insertNewLineAt(31, 12)
      typeAndCommit(
        """//if(true) {
              |//i++; j--;
              |//}""".trimMargin()
      )

      selectBetweenLogicalPositions(
        lineStartIndex = 31,
        columnStartIndex = 14,
        lineEndIndex = 31,
        columnEndIndex = 24
      )
      deleteSymbolAtCaret()
      moveCaretToLogicalPosition(33, 15)
      deleteSymbolAtCaret()
    }

    testInvokeLater(project) {
      TestCase.assertTrue(expectedSuggestion is NoSuggestion)
    }
  }

  override fun `testUnwrap IF written in string block and don't get suggestion`() {
    with(myFixture) {
      insertNewLineAt(31, 12)
      typeAndCommit(
        """String s = "if(true) {
              |i++; j--;
              |}"""".trimMargin()
      )

      selectBetweenLogicalPositions(
        lineStartIndex = 31,
        columnStartIndex = 24,
        lineEndIndex = 31,
        columnEndIndex = 34
      )
      deleteSymbolAtCaret()
      moveCaretToLogicalPosition(33, 22)
      deleteSymbolAtCaret()
    }

    testInvokeLater(project) {
      TestCase.assertTrue(expectedSuggestion is NoSuggestion)
    }
  }
}

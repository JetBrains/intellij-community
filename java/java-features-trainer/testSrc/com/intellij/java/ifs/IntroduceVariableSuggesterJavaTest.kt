// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ifs

import junit.framework.TestCase
import training.featuresSuggester.FeatureSuggesterTestUtils.copyCurrentSelection
import training.featuresSuggester.FeatureSuggesterTestUtils.cutBetweenLogicalPositions
import training.featuresSuggester.FeatureSuggesterTestUtils.deleteSymbolAtCaret
import training.featuresSuggester.FeatureSuggesterTestUtils.insertNewLineAt
import training.featuresSuggester.FeatureSuggesterTestUtils.moveCaretToLogicalPosition
import training.featuresSuggester.FeatureSuggesterTestUtils.pasteFromClipboard
import training.featuresSuggester.FeatureSuggesterTestUtils.selectBetweenLogicalPositions
import training.featuresSuggester.FeatureSuggesterTestUtils.testInvokeLater
import training.featuresSuggester.FeatureSuggesterTestUtils.typeAndCommit
import training.featuresSuggester.IntroduceVariableSuggesterTest
import training.featuresSuggester.NoSuggestion

/**
 * Note: when user is declaring variable and it's name starts with any language keyword suggestion will not be thrown
 */
class IntroduceVariableSuggesterJavaTest : IntroduceVariableSuggesterTest() {
  override val testingCodeFileName = "JavaCodeExample.java"

  override fun getTestDataPath() = JavaSuggestersTestUtils.testDataPath
  override fun `testIntroduce expression from IF and get suggestion`() {
    with(myFixture) {
      cutBetweenLogicalPositions(lineStartIndex = 9, columnStartIndex = 23, lineEndIndex = 9, columnEndIndex = 38)
      insertNewLineAt(9, 8)
      typeAndCommit("boolean flag =")
      pasteFromClipboard()
      typeAndCommit(";")
      moveCaretToLogicalPosition(10, 23)
      typeAndCommit(" flag")
    }

    testInvokeLater(project) {
      assertSuggestedCorrectly()
    }
  }

  override fun `testIntroduce full expression from method call and get suggestion`() {
    with(myFixture) {
      cutBetweenLogicalPositions(lineStartIndex = 10, columnStartIndex = 48, lineEndIndex = 10, columnEndIndex = 31)
      insertNewLineAt(10, 12)
      typeAndCommit("int value = ")
      pasteFromClipboard()
      typeAndCommit(";")
      moveCaretToLogicalPosition(11, 31)
      typeAndCommit("value")
    }

    testInvokeLater(project) {
      assertSuggestedCorrectly()
    }
  }

  override fun `testIntroduce part of expression from method call and get suggestion`() {
    with(myFixture) {
      cutBetweenLogicalPositions(lineStartIndex = 10, columnStartIndex = 40, lineEndIndex = 10, columnEndIndex = 31)
      insertNewLineAt(10, 12)
      typeAndCommit("long val = ")
      pasteFromClipboard()
      typeAndCommit(";")
      moveCaretToLogicalPosition(11, 31)
      typeAndCommit("val")
    }

    testInvokeLater(project) {
      assertSuggestedCorrectly()
    }
  }

  override fun `testIntroduce part of string expression from method call and get suggestion`() {
    with(myFixture) {
      cutBetweenLogicalPositions(lineStartIndex = 47, columnStartIndex = 35, lineEndIndex = 47, columnEndIndex = 46)
      insertNewLineAt(47, 12)
      typeAndCommit("String sss = ")
      pasteFromClipboard()
      typeAndCommit(";")
      moveCaretToLogicalPosition(48, 35)
      typeAndCommit("sss")
    }

    testInvokeLater(project) {
      assertSuggestedCorrectly()
    }
  }

  override fun `testIntroduce full expression from return statement and get suggestion`() {
    with(myFixture) {
      cutBetweenLogicalPositions(lineStartIndex = 25, columnStartIndex = 19, lineEndIndex = 25, columnEndIndex = 63)
      insertNewLineAt(25, 12)
      typeAndCommit("Boolean bool=")
      pasteFromClipboard()
      typeAndCommit(";")
      moveCaretToLogicalPosition(26, 19)
      typeAndCommit("bool")
    }

    testInvokeLater(project) {
      assertSuggestedCorrectly()
    }
  }

  override fun `testIntroduce expression from method body using copy and backspace and get suggestion`() {
    with(myFixture) {
      selectBetweenLogicalPositions(
        lineStartIndex = 38,
        columnStartIndex = 35,
        lineEndIndex = 38,
        columnEndIndex = 40
      )
      copyCurrentSelection()
      selectBetweenLogicalPositions(
        lineStartIndex = 38,
        columnStartIndex = 35,
        lineEndIndex = 38,
        columnEndIndex = 40
      )
      deleteSymbolAtCaret()
      insertNewLineAt(38, 16)
      typeAndCommit("short output =")
      pasteFromClipboard()
      typeAndCommit(";")
      moveCaretToLogicalPosition(39, 35)
      typeAndCommit("output")
    }

    testInvokeLater(project) {
      assertSuggestedCorrectly()
    }
  }

  /**
   * This case must throw suggestion but not working now
   */
  fun `testIntroduce part of string declaration expression and don't get suggestion`() {
    with(myFixture) {
      cutBetweenLogicalPositions(lineStartIndex = 48, columnStartIndex = 25, lineEndIndex = 48, columnEndIndex = 49)
      insertNewLineAt(48, 12)
      typeAndCommit("String string = ")
      pasteFromClipboard()
      typeAndCommit(";")
      moveCaretToLogicalPosition(49, 25)
      typeAndCommit("string")
    }

    testInvokeLater(project) {
      TestCase.assertTrue(expectedSuggestion is NoSuggestion)
    }
  }
}

// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ifs

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import training.featuresSuggester.FeatureSuggesterTestUtils.insertNewLineAt
import training.featuresSuggester.FeatureSuggesterTestUtils.moveCaretToLogicalPosition
import training.featuresSuggester.FeatureSuggesterTestUtils.testInvokeLater
import training.featuresSuggester.FeatureSuggesterTestUtils.typeAndCommit
import training.featuresSuggester.NoSuggestion
import training.featuresSuggester.featureSuggesterFixture

@TestApplication
class LineCommentingSuggesterJavaTest {
  private val suggester by featureSuggesterFixture(JavaSuggestersTestUtils.testDataPath, "JavaCodeExample.java", "Comment with line comment")

  @Test
  fun `testComment 3 lines in a row and get suggestion`(): Unit = suggester.runTest {
    with(myFixture) {
      moveCaretToLogicalPosition(6, 8)
      typeAndCommit("//")
      moveCaretToLogicalPosition(7, 8)
      typeAndCommit("//")
      moveCaretToLogicalPosition(8, 8)
      typeAndCommit("//")
    }

    testInvokeLater(project) {
      assertSuggestedCorrectly()
    }
  }

  @Test
  fun `testComment 3 lines in different order and get suggestion`(): Unit = suggester.runTest {
    with(myFixture) {
      moveCaretToLogicalPosition(9, 5)
      typeAndCommit("//")
      moveCaretToLogicalPosition(11, 0)
      typeAndCommit("//")
      moveCaretToLogicalPosition(10, 8)
      typeAndCommit("//")
    }

    testInvokeLater(project) {
      assertSuggestedCorrectly()
    }
  }

  @Test
  fun `testComment two lines and one empty line and don't get suggestion`(): Unit = suggester.runTest {
    with(myFixture) {
      moveCaretToLogicalPosition(12, 3)
      typeAndCommit("//")
      moveCaretToLogicalPosition(13, 1)
      typeAndCommit("//")
      moveCaretToLogicalPosition(14, 0)
      typeAndCommit("//")
    }

    testInvokeLater(project) {
      assertTrue(expectedSuggestion is NoSuggestion)
    }
  }

  @Test
  fun `testComment two lines in a row and one with interval and don't get suggestion`(): Unit = suggester.runTest {
    with(myFixture) {
      moveCaretToLogicalPosition(32, 0)
      typeAndCommit("//")
      moveCaretToLogicalPosition(33, 0)
      typeAndCommit("//")
      moveCaretToLogicalPosition(35, 0)
      typeAndCommit("//")
    }

    testInvokeLater(project) {
      assertTrue(expectedSuggestion is NoSuggestion)
    }
  }

  @Test
  fun `testComment 3 already commented lines and don't get suggestion`(): Unit = suggester.runTest {
    with(myFixture) {
      insertNewLineAt(42, 12)
      typeAndCommit(
        """//if(true) {
              |//i++; j--;
              |//}""".trimMargin()
      )

      moveCaretToLogicalPosition(42, 2)
      typeAndCommit("//")
      moveCaretToLogicalPosition(43, 2)
      typeAndCommit("//")
      moveCaretToLogicalPosition(44, 2)
      typeAndCommit("//")
    }

    testInvokeLater(project) {
      assertTrue(expectedSuggestion is NoSuggestion)
    }
  }

  @Test
  fun `testComment 3 lines of block comment and don't get suggestion`(): Unit = suggester.runTest {
    with(myFixture) {
      insertNewLineAt(42, 12)
      typeAndCommit(
        """/*
              |if(true) {
              |    i++; j--;
              |}""".trimMargin()
      )

      moveCaretToLogicalPosition(43, 4)
      typeAndCommit("//")
      moveCaretToLogicalPosition(44, 4)
      typeAndCommit("//")
      moveCaretToLogicalPosition(45, 4)
      typeAndCommit("//")
    }

    testInvokeLater(project) {
      assertTrue(expectedSuggestion is NoSuggestion)
    }
  }
}

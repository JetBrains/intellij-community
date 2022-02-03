// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ifs

import junit.framework.TestCase
import training.featuresSuggester.FeatureSuggesterTest
import training.featuresSuggester.FeatureSuggesterTestUtils.copyBetweenLogicalPositions
import training.featuresSuggester.FeatureSuggesterTestUtils.testInvokeLater
import training.featuresSuggester.NoSuggestion

class CopyPasteSuggesterTest : FeatureSuggesterTest() {
  override val testingCodeFileName = "JavaCodeExample.java"
  override val testingSuggesterId = "Paste from history"

  override fun getTestDataPath() = JavaSuggestersTestUtils.testDataPath

  fun `testCopy text that contained in clipboard at first index and get suggestion`() {
    with(myFixture) {
      copyBetweenLogicalPositions(lineStartIndex = 5, columnStartIndex = 8, lineEndIndex = 5, columnEndIndex = 19)
      copyBetweenLogicalPositions(lineStartIndex = 6, columnStartIndex = 20, lineEndIndex = 6, columnEndIndex = 8)
      copyBetweenLogicalPositions(lineStartIndex = 5, columnStartIndex = 8, lineEndIndex = 5, columnEndIndex = 19)
    }

    testInvokeLater(project) {
      assertSuggestedCorrectly()
    }
  }

  fun `testCopy text that contained in clipboard at second index and get suggestion`() {
    with(myFixture) {
      copyBetweenLogicalPositions(lineStartIndex = 24, columnStartIndex = 8, lineEndIndex = 26, columnEndIndex = 9)
      copyBetweenLogicalPositions(lineStartIndex = 28, columnStartIndex = 8, lineEndIndex = 28, columnEndIndex = 47)
      copyBetweenLogicalPositions(lineStartIndex = 28, columnStartIndex = 31, lineEndIndex = 28, columnEndIndex = 39)
      copyBetweenLogicalPositions(lineStartIndex = 26, columnStartIndex = 9, lineEndIndex = 24, columnEndIndex = 8)
    }

    testInvokeLater(project) {
      assertSuggestedCorrectly()
    }
  }

  fun `testCopy same text twice in a row and don't get suggestion`() {
    with(myFixture) {
      copyBetweenLogicalPositions(lineStartIndex = 30, columnStartIndex = 19, lineEndIndex = 30, columnEndIndex = 33)
      copyBetweenLogicalPositions(lineStartIndex = 30, columnStartIndex = 33, lineEndIndex = 30, columnEndIndex = 19)
    }

    testInvokeLater(project) {
      TestCase.assertTrue(expectedSuggestion is NoSuggestion)
    }
  }

  fun `testCopy text that contained in clipboard at third index and don't get suggestion`() {
    with(myFixture) {
      copyBetweenLogicalPositions(lineStartIndex = 24, columnStartIndex = 8, lineEndIndex = 26, columnEndIndex = 9)
      copyBetweenLogicalPositions(lineStartIndex = 28, columnStartIndex = 8, lineEndIndex = 28, columnEndIndex = 47)
      copyBetweenLogicalPositions(lineStartIndex = 28, columnStartIndex = 31, lineEndIndex = 28, columnEndIndex = 39)
      copyBetweenLogicalPositions(lineStartIndex = 38, columnStartIndex = 16, lineEndIndex = 38, columnEndIndex = 42)
      copyBetweenLogicalPositions(lineStartIndex = 26, columnStartIndex = 9, lineEndIndex = 24, columnEndIndex = 8)
    }

    testInvokeLater(project) {
      TestCase.assertTrue(expectedSuggestion is NoSuggestion)
    }
  }
}

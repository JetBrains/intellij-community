// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ifs

import junit.framework.TestCase
import training.featuresSuggester.FeatureSuggesterTestUtils.chooseCompletionItem
import training.featuresSuggester.FeatureSuggesterTestUtils.deleteTextBetweenLogicalPositions
import training.featuresSuggester.FeatureSuggesterTestUtils.invokeCodeCompletion
import training.featuresSuggester.FeatureSuggesterTestUtils.moveCaretToLogicalPosition
import training.featuresSuggester.FeatureSuggesterTestUtils.testInvokeLater
import training.featuresSuggester.FeatureSuggesterTestUtils.typeAndCommit
import training.featuresSuggester.FeatureSuggesterTestUtils.typeDelete
import training.featuresSuggester.NoSuggestion
import training.featuresSuggester.ReplaceCompletionSuggesterTest

class ReplaceCompletionSuggesterJavaTest : ReplaceCompletionSuggesterTest() {
  override val testingCodeFileName = "JavaCodeExample.java"

  override fun getTestDataPath() = JavaSuggestersTestUtils.testDataPath

  override fun `testDelete and type dot, complete method call, remove previous identifier and get suggestion`() {
    with(myFixture) {
      moveCaretToLogicalPosition(12, 20)
      deleteAndTypeDot()
      val variants = invokeCodeCompletion() ?: error("Not found lookup elements")
      chooseCompletionItem(variants[0])
      repeat(5) { typeDelete() }
    }

    testInvokeLater(project) {
      assertSuggestedCorrectly()
    }
  }

  override fun `testCall completion, complete method call, remove previous identifier and get suggestion`() {
    with(myFixture) {
      moveCaretToLogicalPosition(61, 60)
      val completionItem = invokeCodeCompletion()?.find { it.lookupString == "cyclesFunction" } ?: error("Not found lookup elements")
      chooseCompletionItem(completionItem)
      deleteTextBetweenLogicalPositions(
        lineStartIndex = 61,
        columnStartIndex = 75,
        lineEndIndex = 61,
        columnEndIndex = 97
      )
    }

    testInvokeLater(project) {
      assertSuggestedCorrectly()
    }
  }

  override fun `testCall completion, complete with method call, add parameter to method call, remove previous identifier and get suggestion`() {
    with(myFixture) {
      moveCaretToLogicalPosition(61, 60)
      val completionItem = invokeCodeCompletion()?.find { it.lookupString == "cyclesFunction" } ?: error("Not found lookup elements")
      chooseCompletionItem(completionItem)
      typeAndCommit("123")
      deleteTextBetweenLogicalPositions(
        lineStartIndex = 61,
        columnStartIndex = 79,
        lineEndIndex = 61,
        columnEndIndex = 100
      )
    }

    testInvokeLater(project) {
      assertSuggestedCorrectly()
    }
  }

  override fun `testCall completion, complete with property, remove previous identifier and get suggestion`() {
    with(myFixture) {
      moveCaretToLogicalPosition(61, 33)
      val variants = invokeCodeCompletion() ?: error("Not found lookup elements")
      chooseCompletionItem(variants[0])
      repeat(21) { typeDelete() }
    }

    testInvokeLater(project) {
      assertSuggestedCorrectly()
    }
  }

  override fun `testCall completion inside arguments list, complete method call, remove previous identifier and get suggestion`() {
    with(myFixture) {
      moveCaretToLogicalPosition(61, 91)
      val variants = invokeCodeCompletion() ?: error("Not found lookup elements")
      chooseCompletionItem(variants[1])
      repeat(15) { typeDelete() }
    }

    testInvokeLater(project) {
      assertSuggestedCorrectly()
    }
  }

  override fun `testCall completion, type additional characters, complete, remove previous identifier and get suggestion`() {
    with(myFixture) {
      moveCaretToLogicalPosition(61, 33)
      invokeCodeCompletion()
      typeAndCommit("cycles")
      val variants = lookupElements ?: error("Not found lookup elements")
      chooseCompletionItem(variants[0])
      repeat(22) { typeDelete() }
    }

    testInvokeLater(project) {
      assertSuggestedCorrectly()
    }
  }

  override fun `testCall completion, complete method call, remove another equal identifier and don't get suggestion`() {
    with(myFixture) {
      moveCaretToLogicalPosition(61, 60)
      val variants = invokeCodeCompletion() ?: error("Not found lookup elements")
      chooseCompletionItem(variants[1])
      deleteTextBetweenLogicalPositions(
        lineStartIndex = 62,
        columnStartIndex = 20,
        lineEndIndex = 62,
        columnEndIndex = 45
      )
    }

    testInvokeLater(project) {
      TestCase.assertTrue(expectedSuggestion is NoSuggestion)
    }
  }
}

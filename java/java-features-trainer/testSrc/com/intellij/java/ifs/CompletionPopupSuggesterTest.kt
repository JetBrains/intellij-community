// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ifs

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test
import training.featuresSuggester.FeatureSuggesterTestUtils.chooseCompletionItem
import training.featuresSuggester.FeatureSuggesterTestUtils.deleteSymbolAtCaret
import training.featuresSuggester.FeatureSuggesterTestUtils.invokeCodeCompletion
import training.featuresSuggester.FeatureSuggesterTestUtils.moveCaretToLogicalPosition
import training.featuresSuggester.FeatureSuggesterTestUtils.testInvokeLater
import training.featuresSuggester.FeatureSuggesterTestUtils.typeAndCommit
import training.featuresSuggester.featureSuggesterFixture

@TestApplication
class CompletionPopupSuggesterTest {
  private val suggester by featureSuggesterFixture(JavaSuggestersTestUtils.testDataPath, "JavaCodeExample.java", "Completion")

  @Test
  fun `testDelete and type dot, complete method call and get suggestion`(): Unit = suggester.runTest {
    with(myFixture) {
      moveCaretToLogicalPosition(12, 20)
      deleteSymbolAtCaret()
      typeAndCommit(".")
      val variants = invokeCodeCompletion() ?: error("Not found lookup elements")
      chooseCompletionItem(variants[0])
    }

    testInvokeLater(project) {
      assertSuggestedCorrectly()
    }
  }

  @Test
  fun `testDelete and type dot inside arguments list, complete with property and get suggestion`(): Unit = suggester.runTest {
    with(myFixture) {
      moveCaretToLogicalPosition(61, 91)
      deleteSymbolAtCaret()
      typeAndCommit(".")
      val variants = invokeCodeCompletion() ?: error("Not found lookup elements")
      chooseCompletionItem(variants[0])
    }

    testInvokeLater(project) {
      assertSuggestedCorrectly()
    }
  }

  @Test
  fun `testDelete and type dot, type additional characters, complete and get suggestion`(): Unit = suggester.runTest {
    with(myFixture) {
      moveCaretToLogicalPosition(61, 33)
      deleteSymbolAtCaret()
      typeAndCommit(".")
      invokeCodeCompletion()
      typeAndCommit("cycles")
      val variants = lookupElements ?: error("Not found lookup elements")
      chooseCompletionItem(variants[0])
    }

    testInvokeLater(project) {
      assertSuggestedCorrectly()
    }
  }
}

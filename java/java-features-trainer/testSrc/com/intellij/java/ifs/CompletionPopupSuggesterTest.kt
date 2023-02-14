// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ifs

import com.intellij.testFramework.NeedsIndex
import com.intellij.testFramework.TestIndexingModeSupporter
import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import training.featuresSuggester.FeatureSuggesterTest
import training.featuresSuggester.FeatureSuggesterTestUtils.chooseCompletionItem
import training.featuresSuggester.FeatureSuggesterTestUtils.deleteSymbolAtCaret
import training.featuresSuggester.FeatureSuggesterTestUtils.invokeCodeCompletion
import training.featuresSuggester.FeatureSuggesterTestUtils.moveCaretToLogicalPosition
import training.featuresSuggester.FeatureSuggesterTestUtils.testInvokeLater
import training.featuresSuggester.FeatureSuggesterTestUtils.typeAndCommit

@NeedsIndex.SmartMode(reason = "BeforeCompletionChooseItemAction is not DumbAware")
class CompletionPopupSuggesterTest : FeatureSuggesterTest(), TestIndexingModeSupporter {
  override val testingCodeFileName = "JavaCodeExample.java"
  override val testingSuggesterId = "Completion"
  private var indexingSupporterMode: IndexingMode = IndexingMode.SMART

  override fun getTestDataPath() = JavaSuggestersTestUtils.testDataPath

  override fun setUp() {
    super.setUp()
    indexingSupporterMode.setUpTest(myFixture.project, myFixture.testRootDisposable)
  }

  override fun tearDown() {
    try {
      indexingSupporterMode.tearDownTest(myFixture.project)
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  fun `testDelete and type dot, complete method call and get suggestion`() {
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

  fun `testDelete and type dot inside arguments list, complete with property and get suggestion`() {
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

  fun `testDelete and type dot, type additional characters, complete and get suggestion`() {
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

  override fun setIndexingMode(mode: IndexingMode) {
    indexingSupporterMode = mode
  }

  override fun getIndexingMode(): IndexingMode = indexingSupporterMode
}

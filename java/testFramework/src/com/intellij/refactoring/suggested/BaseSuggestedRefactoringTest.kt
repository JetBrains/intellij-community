// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.suggested

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.refactoring.RefactoringBundle
import kotlin.test.assertNotEquals

abstract class BaseSuggestedRefactoringTest : LightJavaCodeInsightFixtureTestCaseWithUtils() {
  protected abstract val fileType: LanguageFileType

  protected var ignoreErrorsBefore = false
  protected var ignoreErrorsAfter = false

  override fun setUp() {
    ignoreErrorsBefore = false
    ignoreErrorsAfter = false
    super.setUp()
  }

  protected fun doTestChangeSignature(
    initialText: String,
    expectedTextAfter: String,
    usagesName: String,
    vararg editingActions: () -> Unit,
    wrapIntoCommandAndWriteAction: Boolean = true,
    expectedPresentation: String? = null
  ) {
    doTest(
      initialText,
      RefactoringBundle.message("suggested.refactoring.change.signature.intention.text", usagesName),
      expectedTextAfter,
      *editingActions,
      wrapIntoCommandAndWriteActionAndCommitAll = wrapIntoCommandAndWriteAction,
      checkPresentation = {
        if (expectedPresentation != null) {
          val state = SuggestedRefactoringProviderImpl.getInstance(project).state!!
            .let { it.refactoringSupport.availability.refineSignaturesWithResolve(it) }
          assertEquals(SuggestedRefactoringState.ErrorLevel.NO_ERRORS, state.errorLevel)
          assertNotEquals(state.oldSignature, state.newSignature)
          val refactoringSupport = state.refactoringSupport
          val data = refactoringSupport.availability.detectAvailableRefactoring(state) as SuggestedChangeSignatureData
          val model = refactoringSupport.ui.buildSignatureChangePresentation(data.oldSignature, data.newSignature)
          assertEquals(expectedPresentation, model.dump().trim())
        }
      }
    )
  }

  protected fun doTestRename(
    initialText: String,
    textAfterRefactoring: String,
    oldName: String,
    newName: String,
    vararg editingActions: () -> Unit,
    wrapIntoCommandAndWriteAction: Boolean = true
  ) {
    doTest(
      initialText,
      RefactoringBundle.message("suggested.refactoring.rename.intention.text", oldName, newName),
      textAfterRefactoring,
      *editingActions,
      wrapIntoCommandAndWriteActionAndCommitAll = wrapIntoCommandAndWriteAction
    )
  }

  private fun doTest(
    initialText: String,
    actionName: String,
    textAfterRefactoring: String,
    vararg editingActions: () -> Unit,
    wrapIntoCommandAndWriteActionAndCommitAll: Boolean = true,
    checkPresentation: () -> Unit = {}
  ) {
    myFixture.configureByText(fileType, initialText)

    if (!ignoreErrorsBefore) {
      myFixture.testHighlighting(false, false, false, myFixture.file.virtualFile)
    }

    executeEditingActions(editingActions, wrapIntoCommandAndWriteActionAndCommitAll)
    checkPresentation()

    val intention = myFixture.findSingleIntention(actionName)
    myFixture.launchAction(intention)
    myFixture.checkResult(textAfterRefactoring)

    if (!ignoreErrorsAfter) {
      myFixture.testHighlighting(false, false, false, myFixture.file.virtualFile)
    }
  }
}

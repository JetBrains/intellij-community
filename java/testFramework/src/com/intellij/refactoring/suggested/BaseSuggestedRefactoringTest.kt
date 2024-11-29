// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.suggested

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.refactoring.RefactoringBundle
import org.junit.Assert.assertNotEquals

abstract class BaseSuggestedRefactoringTest : LightJavaCodeInsightFixtureTestCaseWithUtils() {
  protected abstract val fileType: LanguageFileType

  protected var ignoreErrorsBefore: Boolean = false
  protected var ignoreErrorsAfter: Boolean = false

  override fun setUp() {
    ignoreErrorsBefore = false
    ignoreErrorsAfter = false
    super.setUp()
  }

  protected fun doTestChangeSignature(
    initialText: String,
    expectedTextAfter: String,
    usagesName: String,
    expectedPresentation: String? = null,
    editingActions: () -> Unit,
  ) {
    doTest(
      initialText,
      RefactoringBundle.message("suggested.refactoring.change.signature.intention.text", usagesName),
      expectedTextAfter,
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
      },
      editingActions
    )
  }

  protected fun doTestRename(
    initialText: String,
    textAfterRefactoring: String,
    oldName: String,
    newName: String,
    editingActions: () -> Unit,
  ) {
    doTest(
      initialText,
      RefactoringBundle.message("suggested.refactoring.rename.intention.text", oldName),
      textAfterRefactoring,
      {},
      editingActions
    )
  }

  private fun doTest(
    initialText: String,
    actionName: String,
    textAfterRefactoring: String,
    checkPresentation: () -> Unit,
    editingActions: () -> Unit
  ) {
    myFixture.configureByText(fileType, initialText)

    if (!ignoreErrorsBefore) {
      myFixture.testHighlighting(false, false, false, myFixture.file.virtualFile)
    } else {
      // Invoking highlighting has side effects (in our particular case, we are interested in initializing
      // `SuggestedRefactoringChangeListener`). That's why we have to invoke highlighting even when `ignoreErrorsBefore` is `true`
      myFixture.doHighlighting()
    }

    executeEditingActions(editingActions)

    val intention = suggestedRefactoringIntention()
    assertNotNull("No refactoring available", intention)

    assertEquals("Action name", actionName, intention!!.text)

    checkPresentation()

    executeCommand(project) {
      intention.invoke(project, editor, file)

      runWriteAction {
        PostprocessReformattingAspect.getInstance(project).doPostponedFormatting()
      }
    }

    val index = textAfterRefactoring.indexOf("<caret>")
    if (index >= 0) {
      val text = textAfterRefactoring.substring(0, index) +
                 textAfterRefactoring.substring(index + "<caret>".length)
      assertEquals(text, editor.document.text)

      assertEquals("Caret position", index, editor.caretModel.offset)
    }
    else {
      assertEquals(textAfterRefactoring, editor.document.text)
    }

    if (!ignoreErrorsAfter) {
      myFixture.testHighlighting(false, false, false, myFixture.file.virtualFile)
    }
  }

  protected fun suggestedRefactoringIntention(): IntentionAction? {
    return myFixture.availableIntentions.firstOrNull { it.familyName == "Suggested Refactoring" }
  }
}

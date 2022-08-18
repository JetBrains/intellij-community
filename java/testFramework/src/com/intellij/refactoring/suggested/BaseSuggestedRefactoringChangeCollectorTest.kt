// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.suggested

import com.intellij.lang.Language
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport.Signature
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

abstract class BaseSuggestedRefactoringChangeCollectorTest<TDeclaration : PsiElement> : LightJavaCodeInsightFixtureTestCase() {
  private lateinit var collector: SuggestedRefactoringChangeCollector

  protected abstract val language: Language
  protected abstract val fileType: FileType

  protected abstract fun addDeclaration(file: PsiFile, text: String): TDeclaration

  protected abstract fun Signature.presentation(labelForParameterId: (Any) -> String?): String

  override fun setUp() {
    super.setUp()
    collector = SuggestedRefactoringChangeCollector(SuggestedRefactoringAvailabilityIndicator(project))
  }

  override fun tearDown() {
    try {
      collector.reset()
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  protected fun doTest(
    initialDeclarationText: String,
    vararg editingActions: (TDeclaration) -> Unit,
    wrapIntoCommandAndWriteAction: Boolean = true,
    expectedOldSignature: String?,
    expectedNewSignature: String?
  ) {
    myFixture.configureByText(fileType, "")

    val refactoringSupport = SuggestedRefactoringSupport.forLanguage(language)!!
    var declaration: TDeclaration? = null
    executeCommand {
      runWriteAction {
        declaration = addDeclaration(file, initialDeclarationText)
        collector.editingStarted(declaration!!, refactoringSupport)
        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)
      }
    }

    for (action in editingActions) {
      if (wrapIntoCommandAndWriteAction) {
        executeCommand {
          runWriteAction {
            action(declaration!!)
          }
        }
      }
      else {
        action(declaration!!)
      }
      PsiDocumentManager.getInstance(project).commitAllDocuments()
      require(declaration!!.isValid)
      collector.nextSignature(declaration!!, refactoringSupport)
    }

    checkState(expectedOldSignature, expectedNewSignature)
  }

  private fun checkState(expectedOldSignature: String?, expectedNewSignature: String?) {
    val oldSignature = collector.state?.oldSignature
    val newSignature = collector.state?.newSignature
    assertEquals(
      "Old signature",
      expectedOldSignature,
      oldSignature?.presentation(labelForParameterId = { null })
    )
    assertEquals(
      "New signature",
      expectedNewSignature,
      newSignature?.presentation(labelForParameterId = { id ->
        val oldParameter = oldSignature!!.parameterById(id)
        if (oldParameter != null) {
          "initialIndex = " + oldSignature.parameterIndex(oldParameter)
        }
        else {
          "new"
        }
      })
    )
  }
}
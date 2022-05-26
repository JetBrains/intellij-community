// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.suggested

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import junit.framework.TestCase

abstract class BaseSuggestedRefactoringChangeListenerTest : LightJavaCodeInsightFixtureTestCase() {
  private val watcher = Watcher()
  private lateinit var changeListener: SuggestedRefactoringChangeListener

  protected abstract val fileType: FileType

  private fun StringBuilder.appendSignature(declaration: PsiElement) {
    val refactoringSupport = SuggestedRefactoringSupport.forLanguage(declaration.language)!!
    val range = refactoringSupport.signatureRange(declaration)!!
    append("'")
    val text = declaration.containingFile.text
      .substring(range.startOffset, range.endOffset)
      .replace("\n", "\\n")
    append(text)
    append("'")
  }

  private val disposable = Disposer.newDisposable()

  override fun setUp() {
    super.setUp()

    changeListener = SuggestedRefactoringChangeListener(project, watcher, testRootDisposable)
  }

  override fun tearDown() {
    Disposer.dispose(disposable)
    super.tearDown()
  }

  protected fun setup(fileText: String) {
    myFixture.configureByText(fileType, fileText)
  }

  protected fun perform(vararg expectedLog: String, action: () -> Unit) {
    watcher.check("")
    action()
    executeCommand {
      runWriteAction {
        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)
      }
    }
    watcher.check(expectedLog.joinToString(separator = "\n"))
  }

  protected fun commitAll() {
    PsiDocumentManager.getInstance(project).commitAllDocuments()
  }

  protected fun insertString(offset: Int, text: String) {
    executeCommand {
      runWriteAction {
        editor.document.insertString(offset, text)
      }
    }
  }

  private inner class Watcher : SuggestedRefactoringSignatureWatcher {
    private val log = StringBuilder()

    fun check(expectedLog: String) {
      TestCase.assertEquals(expectedLog, log.toString().trim())
      log.clear()
    }

    override fun editingStarted(declaration: PsiElement, refactoringSupport: SuggestedRefactoringSupport) {
      log.append("editingStarted: ")
      log.appendSignature(declaration)
      log.append("\n")
    }

    override fun nextSignature(anchor: PsiElement, refactoringSupport: SuggestedRefactoringSupport) {
      if (refactoringSupport.hasSyntaxError(anchor)) return

      log.append("nextSignature: ")
      log.appendSignature(anchor)
      log.append("\n")
    }

    override fun inconsistentState() {
      log.append("inconsistentState\n")
    }

    override fun reset() {
      log.append("reset\n")
    }
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actionsOnSave

import com.intellij.ide.actionsOnSave.impl.ActionsOnSaveFileDocumentManagerListener
import com.intellij.ide.actionsOnSave.impl.ActionsOnSaveManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.utils.coroutines.waitCoroutinesBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ActionsOnSaveTest : BasePlatformTestCase() {
  @Service(Service.Level.PROJECT)
  private class ActionsOnSaveTestService(val cs: CoroutineScope) {
    var onBgtActionOnSave1Started: (suspend (Document) -> Unit)? = null
    fun bgtActionOnSave1Started(document: Document) = onBgtActionOnSave1Started?.let { cs.launch { it(document) } }
  }

  private data object EdtActionOnSave1 : EdtActionOnSave(1)
  private data object EdtActionOnSave2 : EdtActionOnSave(2)

  private data object BgtActionOnSave1 : BgtActionOnSave(1) {
    override suspend fun updateDocument(project: Project, document: Document) {
      project.service<ActionsOnSaveTestService>().bgtActionOnSave1Started(document)
      super.updateDocument(project, document)
    }
  }

  private data object BgtActionOnSave2 : BgtActionOnSave(2)

  private sealed class EdtActionOnSave(val index: Int) : ActionsOnSaveFileDocumentManagerListener.ActionOnSave() {
    override fun isEnabledForProject(project: Project): Boolean = true
    override fun processDocuments(project: Project, documents: Array<Document>) {
      documents
        .filter { FileDocumentManager.getInstance().getFile(it)?.name?.endsWith(".txt") == true }
        .takeIf { it.isNotEmpty() }
        ?.let { docs ->
          WriteCommandAction.runWriteCommandAction(project) {
            docs.forEach { document -> document.insertString(document.textLength, "\nEdtActionOnSave$index") }
          }
        }
    }
  }

  private sealed class BgtActionOnSave(val index: Int) : ActionsOnSaveFileDocumentManagerListener.DocumentUpdatingActionOnSave() {
    override val presentableName: String = "BgtActionOnSave$index"
    override fun isEnabledForProject(project: Project): Boolean = true

    override suspend fun updateDocument(project: Project, document: Document) {
      if (FileDocumentManager.getInstance().getFile(document)?.name?.endsWith(".txt") != true) return

      delay(100)
      writeCommandAction(project, "BgtActionOnSave$index(1)") {
        document.insertString(document.textLength, "\nBgtActionOnSave$index(1)")
      }

      delay(100)
      writeCommandAction(project, "BgtActionOnSave$index(2)") {
        document.insertString(document.textLength, "\nBgtActionOnSave$index(2)")
      }
    }
  }

  override fun setUp() {
    super.setUp()

    val ep = ExtensionPointName<ActionsOnSaveFileDocumentManagerListener.ActionOnSave>("com.intellij.actionOnSave").point
    ep.registerExtension(EdtActionOnSave1, testRootDisposable)
    ep.registerExtension(EdtActionOnSave2, testRootDisposable)
    ep.registerExtension(BgtActionOnSave1, testRootDisposable)
    ep.registerExtension(BgtActionOnSave2, testRootDisposable)
  }

  private val file1Name = "file1.txt"
  private val file2Name = "file2.txt"
  private fun isDocument1(document: Document) = FileDocumentManager.getInstance().getFile(document)?.name == file1Name
  private fun isDocument2(document: Document) = FileDocumentManager.getInstance().getFile(document)?.name == file2Name

  private val textAfterAllActions = """
    initial text
    EdtActionOnSave1
    EdtActionOnSave2
    BgtActionOnSave1(1)
    BgtActionOnSave1(2)
    BgtActionOnSave2(1)
    BgtActionOnSave2(2)
  """.trimIndent()

  private fun addDocument(fileName: String): Document {
    val document = myFixture.addFileToProject(fileName, "").fileDocument
    WriteCommandAction.runWriteCommandAction(project) {
      document.insertString(0, "initial text")
    }
    return document
  }

  private suspend fun typeInDocumentAfterDelay(document: Document, delayMillis: Long) {
    delay(delayMillis)
    writeCommandAction(project, "manual typing") {
      document.insertString(document.textLength, "\nmanual typing")
    }
  }

  private fun doTestWithTwoFiles(
    doc1ExpectedText: String,
    doc2ExpectedText: String,
    onBgtActionOnSave1Started: (suspend (document: Document) -> Unit)? = null,
  ) {
    val testService = project.service<ActionsOnSaveTestService>()
    testService.onBgtActionOnSave1Started = onBgtActionOnSave1Started

    val doc1 = addDocument(file1Name)
    val doc2 = addDocument(file2Name)

    FileDocumentManager.getInstance().saveAllDocuments()

    testService.cs.launch {
      ActionsOnSaveManager.getInstance(project).awaitPendingActions()
    }
    waitCoroutinesBlocking(testService.cs)

    checkResult(doc1, doc1ExpectedText, doc2, doc2ExpectedText)
  }

  private fun checkResult(doc1: Document, doc1ExpectedText: String, doc2: Document, doc2ExpectedText: String) {
    assertEquals(file1Name, doc1ExpectedText, doc1.text)
    assertEquals(file2Name, doc2ExpectedText, doc2.text)

    val doc1ExpectedSaved = doc1ExpectedText == textAfterAllActions
    val doc2ExpectedSaved = doc2ExpectedText == textAfterAllActions
    assertEquals("File 1 should be ${if (doc1ExpectedSaved) "saved" else "unsaved"}",
                 doc1ExpectedSaved, !FileDocumentManager.getInstance().isDocumentUnsaved(doc1))
    assertEquals("File 2 should be ${if (doc2ExpectedSaved) "saved" else "unsaved"}",
                 doc2ExpectedSaved, !FileDocumentManager.getInstance().isDocumentUnsaved(doc2))
  }

  fun testAllActionsFinished() =
    doTestWithTwoFiles(textAfterAllActions, textAfterAllActions)

  fun testFirstFileEditedAfterAllActionsFinished() {
    val doc1ExpectedText = "$textAfterAllActions\nmanual typing"
    doTestWithTwoFiles(doc1ExpectedText, textAfterAllActions) {
      if (isDocument1(it)) typeInDocumentAfterDelay(it, 450)
    }
  }

  fun testSecondFileEditedAfter50ms() {
    val doc2ExpectedText = "initial text\nEdtActionOnSave1\nEdtActionOnSave2\nmanual typing"
    doTestWithTwoFiles(textAfterAllActions, doc2ExpectedText) {
      if (isDocument2(it)) typeInDocumentAfterDelay(it, 50)
    }
  }

  fun testBothFilesEditedBeforeBgtActionsGetChance() {
    val docExpectedText = "initial text\nEdtActionOnSave1\nEdtActionOnSave2\nmanual typing"
    var typed = false
    doTestWithTwoFiles(docExpectedText, docExpectedText) {
      if (typed) return@doTestWithTwoFiles
      typed = true

      val (doc1, doc2) = readAction {
        val manager = FileDocumentManager.getInstance()
        val parentDir = manager.getFile(it)!!.parent
        manager.getDocument(parentDir.findChild(file1Name)!!)!! to manager.getDocument(parentDir.findChild(file2Name)!!)!!
      }
      typeInDocumentAfterDelay(doc1, 0)
      typeInDocumentAfterDelay(doc2, 0)
    }
  }

  fun testFirstFileEditedAfter150ms() {
    val doc1ExpectedText = "initial text\nEdtActionOnSave1\nEdtActionOnSave2\nBgtActionOnSave1(1)\nmanual typing"
    doTestWithTwoFiles(doc1ExpectedText, textAfterAllActions) {
      if (isDocument1(it)) typeInDocumentAfterDelay(it, 150)
    }
  }

  fun testFirstFileEditedAfter250msSecondAfter350() {
    val doc1ExpectedText = "initial text\nEdtActionOnSave1\nEdtActionOnSave2\nBgtActionOnSave1(1)\nBgtActionOnSave1(2)\nmanual typing"
    val doc2ExpectedText = "initial text\nEdtActionOnSave1\nEdtActionOnSave2\nBgtActionOnSave1(1)\nBgtActionOnSave1(2)\nBgtActionOnSave2(1)\nmanual typing"
    doTestWithTwoFiles(doc1ExpectedText, doc2ExpectedText) {
      if (isDocument1(it)) typeInDocumentAfterDelay(it, 250)
      if (isDocument2(it)) typeInDocumentAfterDelay(it, 350)
    }
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actionsOnSave

import com.intellij.ide.actionsOnSave.impl.ActionsOnSaveFileDocumentManagerListener
import com.intellij.ide.actionsOnSave.impl.ActionsOnSaveManager
import com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction
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
import kotlinx.coroutines.launch

class ActionsOnSaveTest : BasePlatformTestCase() {

  /**
   * Simulates user typing in the editor while Actions on Save are still in progress.
   * Typing must not be within the same coroutine where Actions on Save run, that's why `cs.launch{}` is used.
   * To have stable test results, `join()` is used, otherwise there would be a race between running Actions on Save and typing.
   */
  @Service(Service.Level.PROJECT)
  private class ActionsOnSaveTestService(private val project: Project, val cs: CoroutineScope) {
    var interceptor: ActionOnSaveInterceptor? = null

    suspend fun actionStarting(actionIndex: Int, document: Document) =
      interceptor?.let { cs.launch { writeCommandAction(project, "") { it.actionStarting(actionIndex, document) } }.join() }

    suspend fun actionHalfDone(actionIndex: Int, document: Document) =
      interceptor?.let { cs.launch { writeCommandAction(project, "") { it.actionHalfDone(actionIndex, document) } }.join() }

    suspend fun actionFinished(actionIndex: Int, document: Document) =
      interceptor?.let { cs.launch { writeCommandAction(project, "") { it.actionFinished(actionIndex, document) } }.join() }
  }

  private open class ActionOnSaveInterceptor {
    open fun actionStarting(actionIndex: Int, document: Document) {}
    open fun actionHalfDone(actionIndex: Int, document: Document) {}
    open fun actionFinished(actionIndex: Int, document: Document) {}
  }

  private data object EdtActionOnSave1 : EdtActionOnSave(1)
  private data object EdtActionOnSave2 : EdtActionOnSave(2)

  private sealed class EdtActionOnSave(val index: Int) : ActionsOnSaveFileDocumentManagerListener.ActionOnSave() {
    override fun isEnabledForProject(project: Project): Boolean = true
    override fun processDocuments(project: Project, documents: Array<Document>) {
      documents
        .filter { FileDocumentManager.getInstance().getFile(it)?.name?.endsWith(".txt") == true }
        .takeIf { it.isNotEmpty() }
        ?.let { docs ->
          runWriteCommandAction(project) {
            docs.forEach { document -> document.insertString(document.textLength, "\nEdtActionOnSave$index") }
          }
        }
    }
  }

  private data object BgtActionOnSave1 : BgtActionOnSave(1)
  private data object BgtActionOnSave2 : BgtActionOnSave(2)

  private sealed class BgtActionOnSave(val index: Int) : ActionsOnSaveFileDocumentManagerListener.DocumentUpdatingActionOnSave() {
    override val presentableName: String = "BgtActionOnSave$index"
    override fun isEnabledForProject(project: Project): Boolean = true

    override suspend fun updateDocument(project: Project, document: Document) {
      if (FileDocumentManager.getInstance().getFile(document)?.name?.endsWith(".txt") != true) return

      val testService = project.service<ActionsOnSaveTestService>()
      testService.actionStarting(index, document)
      writeCommandAction(project, "BgtActionOnSave$index(1)") {
        document.insertString(document.textLength, "\nBgtActionOnSave$index(1)")
      }
      testService.actionHalfDone(index, document)
      writeCommandAction(project, "BgtActionOnSave$index(2)") {
        document.insertString(document.textLength, "\nBgtActionOnSave$index(2)")
      }
      testService.actionFinished(index, document)
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
    runWriteCommandAction(project) {
      document.insertString(0, "initial text")
    }
    return document
  }

  private fun doTestWithTwoFiles(interceptor: ActionOnSaveInterceptor?, doc1ExpectedText: String, doc2ExpectedText: String) {
    val testService = project.service<ActionsOnSaveTestService>()
    testService.interceptor = interceptor

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
    doTestWithTwoFiles(null, textAfterAllActions, textAfterAllActions)

  fun testBothFilesEditedBeforeBgtActionsStart() {
    val interceptor = object : ActionOnSaveInterceptor() {
      override fun actionStarting(actionIndex: Int, document: Document) {
        val manager = FileDocumentManager.getInstance()
        val parentDir = manager.getFile(document)!!.parent
        val doc1 = manager.getDocument(parentDir.findChild(file1Name)!!)!!
        val doc2 = manager.getDocument(parentDir.findChild(file2Name)!!)!!
        doc1.insertString(doc1.textLength, "\nmanual typing")
        doc2.insertString(doc2.textLength, "\nmanual typing")
      }
    }
    val docExpectedText = "initial text\nEdtActionOnSave1\nEdtActionOnSave2\nmanual typing"
    doTestWithTwoFiles(interceptor, docExpectedText, docExpectedText)
  }

  fun testSecondFileEditedBeforeItsBgtActionsStart() {
    val interceptor = object : ActionOnSaveInterceptor() {
      override fun actionStarting(actionIndex: Int, document: Document) {
        if (isDocument2(document)) {
          document.insertString(document.textLength, "\nmanual typing")
        }
      }
    }
    val doc2ExpectedText = "initial text\nEdtActionOnSave1\nEdtActionOnSave2\nmanual typing"
    doTestWithTwoFiles(interceptor, textAfterAllActions, doc2ExpectedText)
  }

  fun testFirstFileEditedAfterItsActionsFinished() {
    val interceptor = object : ActionOnSaveInterceptor() {
      override fun actionFinished(actionIndex: Int, document: Document) {
        if (isDocument1(document) && actionIndex == 2) {
          document.insertString(document.textLength, "\nmanual typing")
        }
      }
    }
    val doc1ExpectedText = "$textAfterAllActions\nmanual typing"
    doTestWithTwoFiles(interceptor, doc1ExpectedText, textAfterAllActions)
  }

  fun testFirstFileEditedInTheMiddle() {
    val interceptor = object : ActionOnSaveInterceptor() {
      override fun actionHalfDone(actionIndex: Int, document: Document) {
        if (isDocument1(document)) {
          document.insertString(document.textLength, "\nmanual typing")
        }
      }
    }
    val doc1ExpectedText = "initial text\nEdtActionOnSave1\nEdtActionOnSave2\nBgtActionOnSave1(1)\nmanual typing"
    doTestWithTwoFiles(interceptor, doc1ExpectedText, textAfterAllActions)
  }


  fun testSecondFileEditedInTheMiddle() {
    val interceptor = object : ActionOnSaveInterceptor() {
      override fun actionStarting(actionIndex: Int, document: Document) {
        if (isDocument2(document) && actionIndex == 2) {
          document.insertString(document.textLength, "\nmanual typing")
        }
      }
    }
    val doc2ExpectedText = "initial text\nEdtActionOnSave1\nEdtActionOnSave2\nBgtActionOnSave1(1)\nBgtActionOnSave1(2)\nmanual typing"
    doTestWithTwoFiles(interceptor, textAfterAllActions, doc2ExpectedText)
  }

  fun testBothFilesEditedInTheMiddle() {
    val interceptor = object : ActionOnSaveInterceptor() {
      override fun actionStarting(actionIndex: Int, document: Document) {
        if (isDocument1(document) && actionIndex == 2) {
          document.insertString(document.textLength, "\nmanual typing")
        }
      }

      override fun actionHalfDone(actionIndex: Int, document: Document) {
        if (isDocument2(document) && actionIndex == 2) {
          document.insertString(document.textLength, "\nmanual typing")
        }
      }
    }

    val doc1ExpectedText = "initial text\nEdtActionOnSave1\nEdtActionOnSave2\nBgtActionOnSave1(1)\nBgtActionOnSave1(2)\nmanual typing"
    val doc2ExpectedText = "initial text\nEdtActionOnSave1\nEdtActionOnSave2\nBgtActionOnSave1(1)\nBgtActionOnSave1(2)\nBgtActionOnSave2(1)\nmanual typing"
    doTestWithTwoFiles(interceptor, doc1ExpectedText, doc2ExpectedText)
  }
}

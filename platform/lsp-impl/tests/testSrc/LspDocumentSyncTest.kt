// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.platform.lsp.common.fakeLspIntegrationFixture
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.TextDocumentSyncOptions
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/**
 * The document lifecycle notifications: `textDocument/didOpen`, `textDocument/didChange` and `textDocument/didClose`.
 *
 * @see LspDidSaveTest
 * @see LspNotebookDocumentTest
 */
@TestApplication
internal class LspDocumentSyncTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  /**
   * Each nested class declares its own: the fixture stores the server capabilities in the shared project,
   * so a second one active in the same test would fail.
   */
  private fun documentSyncIntegrationFixture(syncKind: TextDocumentSyncKind) =
    projectFixture.fakeLspIntegrationFixture(
      configureServerCapabilities = {
        textDocumentSync = Either.forRight(TextDocumentSyncOptions().apply {
          openClose = true
          change = syncKind
        })
      },
    )

  @Nested
  inner class FullSync {
    private val fakeLspIntegration by documentSyncIntegrationFixture(TextDocumentSyncKind.Full)

    @Test
    fun `textDocument didOpen -- server receives notification when file opens`() = timeoutRunBlocking {
      // Open the first file to bootstrap the server session
      val bootstrapFile = codeInsightFixture.configureByText("bootstrap.txt", "bootstrap").virtualFile
      val serverSession = fakeLspIntegration.serverSession(bootstrapFile)

      // Now prepare to open a second file and await its didOpen notification
      val secondFile = codeInsightFixture.addFileToProject("opened.txt", "hello world").virtualFile
      val expectedUri = serverSession.fileUri(secondFile)

      serverSession.expectNotification(serverSession.DID_OPEN) {
        it.textDocument.uri == expectedUri
      }

      assertFalse(FileEditorManager.getInstance(project).isFileOpen(secondFile))

      // Opening the file in an editor triggers didOpen
      withContext(Dispatchers.EDT) {
        FileEditorManager.getInstance(project).openFile(secondFile, true)
      }

      withTimeout(1.seconds) {
        serverSession.awaitExpected()
      }
    }

    @Test
    fun `textDocument didClose -- server receives notification when file closes`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("closed.txt", "content to close").virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      val didClose = serverSession.expectNotification(serverSession.DID_CLOSE) {
        it.textDocument.uri == fileUri
      }

      withContext(Dispatchers.EDT) {
        FileEditorManager.getInstance(project).closeFile(virtualFile)
      }

      didClose.await()
    }

    @Test
    fun `textDocument didChange -- server receives notification with updated content`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("changed.txt", "original").virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      val didChange = serverSession.expectNotification(serverSession.DID_CHANGE) {
        it.textDocument.uri == fileUri &&
        it.contentChanges.size == 1 &&
        it.contentChanges[0].range == null &&
        it.contentChanges[0].text == "modified content"
      }

      edtWriteAction {
        codeInsightFixture.editor.document.setText("modified content")
      }

      didChange.await()
    }
  }

  @Nested
  inner class IncrementalSync {
    private val fakeLspIntegration by documentSyncIntegrationFixture(TextDocumentSyncKind.Incremental)

    @Test
    fun `textDocument didChange -- incremental sync sends notification on document change`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("incr.txt", "original text").virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      val didChange = serverSession.expectNotification(serverSession.DID_CHANGE) {
        it.textDocument.uri == fileUri &&
        it.contentChanges.size == 1 &&
        it.contentChanges[0].range == Range(Position(0, 0), Position(0, "original text".length)) &&
        it.contentChanges[0].text == "modified"
      }

      edtWriteAction {
        CommandProcessor.getInstance().executeCommand(project, {
          codeInsightFixture.editor.document.replaceString(0, "original text".length, "modified")
        }, "test", null)
      }

      didChange.await()
    }
  }
}

package com.intellij.platform.lsp

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.platform.lsp.common.TestNotebookDocumentAdapter
import com.intellij.platform.lsp.common.configureServerSession
import com.intellij.platform.lsp.common.fakeLspServerProviderFixture
import com.intellij.platform.lsp.impl.LspDocumentAdapter
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.extensionPointFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.eclipse.lsp4j.NotebookDocumentSyncRegistrationOptions
import org.eclipse.lsp4j.NotebookSelector
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.TextDocumentSyncOptions
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.Test


@TestApplication
internal class LspTextDocumentIncrementalSyncTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  @Suppress("unused")
  private val fakeLspServerProvider by projectFixture.fakeLspServerProviderFixture(
    configureServerCapabilities = {
      textDocumentSync = Either.forRight(TextDocumentSyncOptions().apply {
        openClose = true
        change = TextDocumentSyncKind.Incremental
        save = Either.forLeft(true)
      })
    },
  )

  @Test
  fun `textDocument didChange -- incremental sync sends notification on document change`() = timeoutRunBlocking {
    val virtualFile = codeInsightFixture.configureByText("incr.txt", "original text").virtualFile
    val serverSession = configureServerSession(project, virtualFile)
    val fileUri = serverSession.fileUri(virtualFile)

    val didChange = serverSession.expectNotification(serverSession.DID_CHANGE) {
      it.textDocument.uri == fileUri &&
      it.contentChanges.size == 1 &&
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


@TestApplication
internal class LspNotebookIncrementalSyncTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)

    @Suppress("unused")
    private val adapterFixture = extensionPointFixture(LspDocumentAdapter.EP_NAME) {
      TestNotebookDocumentAdapter()
    }
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  @Suppress("unused")
  private val fakeLspServerProvider by projectFixture.fakeLspServerProviderFixture(
    configureServerCapabilities = {
      textDocumentSync = Either.forRight(TextDocumentSyncOptions().apply {
        openClose = true
        change = TextDocumentSyncKind.Incremental
        save = Either.forLeft(true)
      })
      notebookDocumentSync = NotebookDocumentSyncRegistrationOptions().apply {
        notebookSelector = listOf(
          NotebookSelector().apply {
            notebook = Either.forLeft("test-notebook")
          }
        )
        save = true
      }
    },
  )

  @Test
  fun `notebookDocument didChange -- incremental sync sends notebook change notification`() = timeoutRunBlocking {
    val content = "cell zero\n---\ncell one"
    val virtualFile = codeInsightFixture.configureByText("test.test-notebook", content).virtualFile
    val serverSession = configureServerSession(project, virtualFile)
    val fileUri = serverSession.fileUri(virtualFile)

    val didChange = serverSession.expectNotification(serverSession.NOTEBOOK_DID_CHANGE) { params ->
      params.notebookDocument.uri == fileUri
    }

    edtWriteAction {
      CommandProcessor.getInstance().executeCommand(project, {
        codeInsightFixture.editor.document.replaceString(0, "cell zero".length, "cell zero modified")
      }, "test", null)
    }

    didChange.await()
  }
}

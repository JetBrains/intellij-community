package com.intellij.platform.lsp

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.common.FakeLspServerDescriptor
import com.intellij.platform.lsp.common.FakeLspServerSupportProvider
import com.intellij.platform.lsp.common.configureServerSession
import com.intellij.platform.lsp.common.fakeLspServerProviderFixture
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.SaveOptions
import org.eclipse.lsp4j.TextDocumentSyncOptions
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

@TestApplication
class LspDidSaveTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  @Nested
  inner class DidSaveWithoutText {
    @Suppress("unused")
    private val fakeLspServerProvider by projectFixture.fakeLspServerProviderFixture(
      configureServerCapabilities = {
        textDocumentSync = Either.forRight(TextDocumentSyncOptions().apply {
          openClose = true
          save = Either.forRight(SaveOptions(false))
        })
      },
    )

    @Test
    fun `didSave notification does not include text when includeText is false`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "initial content").virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectNotification(serverSession.DID_SAVE) {
        it.textDocument.uri == fileUri && it.text == null
      }

      withContext(Dispatchers.EDT) {
        edtWriteAction {
          codeInsightFixture.editor.document.setText("modified content")
        }
        FileDocumentManager.getInstance().saveAllDocuments()
      }

      serverSession.awaitExpected()
    }
  }

  @Nested
  inner class DidSaveWithText {
    @Suppress("unused")
    private val fakeLspServerProvider by projectFixture.fakeLspServerProviderFixture(
      configureServerCapabilities = {
        textDocumentSync = Either.forRight(TextDocumentSyncOptions().apply {
          openClose = true
          save = Either.forRight(SaveOptions(true))
        })
      },
    )

    @Test
    fun `didSave notification includes text when includeText is true`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "initial content").virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      val modifiedText = "modified content"

      serverSession.expectNotification(serverSession.DID_SAVE) {
        it.textDocument.uri == fileUri && it.text == modifiedText
      }

      withContext(Dispatchers.EDT) {
        edtWriteAction {
          codeInsightFixture.editor.document.setText(modifiedText)
        }
        FileDocumentManager.getInstance().saveAllDocuments()
      }

      serverSession.awaitExpected()
    }
  }

  @Nested
  inner class TwoProjects {
    private val tempDirFixture2 = tempPathFixture()
    private val projectFixture2 = projectFixture(tempDirFixture2, openAfterCreation = true)
    private val project2 by projectFixture2

    @Suppress("unused")
    private val moduleFixture2 = projectFixture2.moduleFixture(tempDirFixture2, addPathToSourceRoot = true)

    private val codeInsightFixture2 by codeInsightFixture(projectFixture2, tempDirFixture2)

    @Suppress("unused")
    private val fakeLspServerProvider1 by projectFixture.fakeLspServerProviderFixture(
      configureServerCapabilities = {
        textDocumentSync = Either.forRight(TextDocumentSyncOptions().apply {
          openClose = true
          save = Either.forRight(SaveOptions(false))
        })
      },
    )

    @Suppress("unused")
    private val fakeLspServerProvider2 by projectFixture2.fakeLspServerProviderFixture(
      configureServerCapabilities = {
        textDocumentSync = Either.forRight(TextDocumentSyncOptions().apply {
          openClose = true
          save = Either.forRight(SaveOptions(false))
        })
      },
    )

    @Test
    fun `didSave notification is sent only once when two projects are open`(): Unit = timeoutRunBlocking {
      // Open a file in project1 and start LSP server for it
      val virtualFile1 = codeInsightFixture.configureByText("test1.txt", "content1").virtualFile
      val serverSession1 = configureServerSession(project, virtualFile1)

      // Open a file in project2 and start LSP server for it
      val virtualFile2 = codeInsightFixture2.configureByText("test2.txt", "content2").virtualFile
      configureServerSession(project2, virtualFile2)

      // Get the fake servers for both projects to track notifications
      val servers1 = LspServerManager.getInstance(project).getServersForProvider(FakeLspServerSupportProvider::class.java)
      val descriptor1 = servers1.first().descriptor as FakeLspServerDescriptor
      val fakeServer1 = descriptor1.server

      val servers2 = LspServerManager.getInstance(project2).getServersForProvider(FakeLspServerSupportProvider::class.java)
      val descriptor2 = servers2.first().descriptor as FakeLspServerDescriptor
      val fakeServer2 = descriptor2.server

      val fileUri1 = serverSession1.fileUri(virtualFile1)

      // Expect exactly one didSave notification for project1's file
      serverSession1.expectNotification(serverSession1.DID_SAVE) {
        it.textDocument.uri == fileUri1
      }

      // Modify and save file in project1
      withContext(Dispatchers.EDT) {
        edtWriteAction {
          codeInsightFixture.editor.document.setText("modified content1")
        }
        FileDocumentManager.getInstance().saveAllDocuments()
      }

      // Wait for the expected notification
      serverSession1.awaitExpected()

      // Wait a bit more for any potential duplicate notifications to arrive
      delay(100.milliseconds)

      // Verify project2's server did not receive any didSave notifications
      assertEquals(0, fakeServer2.getDidSaveNotificationCount(),
                   "Project2's LSP server should not receive didSave notification for project1's file")

      // Verify project1's server received exactly one didSave notification (not duplicates)
      assertEquals(1, fakeServer1.getDidSaveNotificationCount(),
                   "Project1's LSP server should receive exactly one didSave notification")
    }
  }
}
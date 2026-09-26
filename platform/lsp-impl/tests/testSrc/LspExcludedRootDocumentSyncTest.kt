// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp

import com.intellij.idea.TestFor
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.platform.lsp.common.configureServerSession
import com.intellij.platform.lsp.common.fakeLspServerProviderFixture
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.TextDocumentSyncOptions
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/** An open file that joins or leaves the project content through an excluded root. */
@TestApplication
internal class LspExcludedRootDocumentSyncTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)
    private val module by moduleFixture
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  @Suppress("unused")
  private val fakeLspServerProvider by projectFixture.fakeLspServerProviderFixture(
    configureServerCapabilities = {
      textDocumentSync = Either.forRight(TextDocumentSyncOptions().apply {
        openClose = true
        change = TextDocumentSyncKind.Full
      })
    },
  )

  @Test
  @TestFor(issues = ["IJPL-256620"])
  fun `un-excluding the directory of an open file sends didOpen`(): Unit = timeoutRunBlocking {
    val bootstrap = codeInsightFixture.configureByText("bootstrap.txt", "bootstrap").virtualFile
    val serverSession = configureServerSession(project, bootstrap)
    val file = codeInsightFixture.addFileToProject("excluded/inside.txt", "hello").virtualFile
    val directory = file.parent
    edtWriteAction { PsiTestUtil.addExcludedRoot(module, directory) }
    var excluded = true
    try {
      withContext(Dispatchers.EDT) { FileEditorManager.getInstance(project).openFile(file, true) }
      val client = LspClientManagerImpl.getInstanceImpl(project).getRunningClients().single()
      assertFalse(readAction { client.isFileOpened(file) }, "an excluded file must not be opened on the server")

      serverSession.expectNotification(serverSession.DID_OPEN) { it.textDocument.uri == serverSession.fileUri(file) }
      edtWriteAction { PsiTestUtil.removeExcludedRoot(module, directory) }
      excluded = false
      withTimeout(10.seconds) { serverSession.awaitExpected() }
    }
    finally {
      if (excluded) edtWriteAction { PsiTestUtil.removeExcludedRoot(module, directory) }
    }
  }

  @Test
  @TestFor(issues = ["IJPL-256620"])
  fun `excluding the directory of an open file sends didClose`(): Unit = timeoutRunBlocking {
    val bootstrap = codeInsightFixture.configureByText("bootstrap.txt", "bootstrap").virtualFile
    val serverSession = configureServerSession(project, bootstrap)
    val file = codeInsightFixture.addFileToProject("excluded/inside.txt", "hello").virtualFile
    val directory = file.parent

    serverSession.expectNotification(serverSession.DID_OPEN) { it.textDocument.uri == serverSession.fileUri(file) }
    withContext(Dispatchers.EDT) { FileEditorManager.getInstance(project).openFile(file, true) }
    withTimeout(10.seconds) { serverSession.awaitExpected() }

    serverSession.expectNotification(serverSession.DID_CLOSE) { it.textDocument.uri == serverSession.fileUri(file) }
    edtWriteAction { PsiTestUtil.addExcludedRoot(module, directory) }
    try {
      withTimeout(10.seconds) { serverSession.awaitExpected() }
    }
    finally {
      edtWriteAction { PsiTestUtil.removeExcludedRoot(module, directory) }
    }
  }
}

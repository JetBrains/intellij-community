// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp

import com.intellij.idea.TestFor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspBulkIntegrationProvider
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManagerListener
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.lsp.common.FakeLspClientDescriptor
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.platform.lsp.impl.documentSync.LspOpenedFilesService
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.extensionPointFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@TestApplication
@TestFor(issues = ["IJPL-254921"])
internal class LspBulkIntegrationProviderTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  private val provider by extensionPointFixture(LspIntegrationProvider.EP_NAME) { TwoServersLspProvider() }

  @Test
  fun `two descriptors that support the same file both start`() = timeoutRunBlocking(2.minutes) {
    val file = codeInsightFixture.addFileToProject("opened.txt", "content").virtualFile
    val manager = LspClientManagerImpl.getInstanceImpl(project)

    awaitRunningClients(manager, 2) { LspOpenedFilesService.getInstance(project).processOpenedFiles(listOf(file)) }
    assertEquals(listOf("A", "B"), clientNames(manager))

    stopClientsAndWait(manager)
  }

  @Test
  fun `a restart starts every descriptor for the open file`() = timeoutRunBlocking(2.minutes) {
    val manager = LspClientManagerImpl.getInstanceImpl(project)

    awaitRunningClients(manager, 2) { codeInsightFixture.configureByText("restarted.txt", "content") }
    assertEquals(listOf("A", "B"), clientNames(manager))

    awaitRunningClients(manager, 2) { manager.stopAndRestartClientsIfNeeded(provider.javaClass) }
    assertEquals(listOf("A", "B"), clientNames(manager))

    stopClientsAndWait(manager)
  }

  @Test
  fun `a start request starts a descriptor whose sibling runs`() = timeoutRunBlocking(2.minutes) {
    val manager = LspClientManagerImpl.getInstanceImpl(project)
    awaitRunningClients(manager, 2) { codeInsightFixture.configureByText("started.txt", "content") }

    manager.stopRunningServer(manager.getClients(provider.javaClass).single { it.descriptor.presentableName == "A" })
    awaitRunningClients(manager, 1) { manager.startClientsIfNeeded(provider.javaClass) }
    assertEquals(listOf("A", "B"), clientNames(manager))

    stopClientsAndWait(manager)
  }

  @Test
  fun `an opened file starts a descriptor whose sibling runs`() = timeoutRunBlocking(2.minutes) {
    val file = codeInsightFixture.addFileToProject("reopened.txt", "content").virtualFile
    val manager = LspClientManagerImpl.getInstanceImpl(project)
    val service = LspOpenedFilesService.getInstance(project)
    awaitRunningClients(manager, 2) { service.processOpenedFiles(listOf(file)) }

    manager.stopRunningServer(manager.getClients(provider.javaClass).single { it.descriptor.presentableName == "A" })
    awaitRunningClients(manager, 1) { service.processOpenedFiles(listOf(file)) }
    assertEquals(listOf("A", "B"), clientNames(manager))

    stopClientsAndWait(manager)
  }

  private fun clientNames(manager: LspClientManagerImpl): List<String> =
    manager.getClients(provider.javaClass).map { it.descriptor.presentableName }.sorted()

  /** Waits for [count] clients to become running, or gives up after a timeout, so the caller's assertion shows which ones run. */
  private suspend fun awaitRunningClients(manager: LspClientManagerImpl, count: Int, action: suspend () -> Unit) {
    val running = Channel<LspClient>(Channel.UNLIMITED)
    val disposable = Disposer.newDisposable("LspBulkIntegrationProviderTest")
    try {
      manager.addListener(object : LspClientManagerListener {
        override fun serverStateChanged(lspClient: LspClient) {
          if (lspClient.state == LspServerState.Running) running.trySend(lspClient)
        }
      }, disposable, false)
      action()
      withTimeoutOrNull(30.seconds) { repeat(count) { running.receive() } }
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  private suspend fun stopClientsAndWait(manager: LspClientManagerImpl) {
    val removed = Channel<LspClient>(Channel.UNLIMITED)
    val disposable = Disposer.newDisposable("LspBulkIntegrationProviderTest")
    try {
      manager.addListener(object : LspClientManagerListener {
        override fun clientRemoved(lspClient: LspClient) {
          removed.trySend(lspClient)
        }
      }, disposable, false)
      val count = manager.getClients(provider.javaClass).size
      manager.stopClients(provider.javaClass)
      repeat(count) { removed.receive() }
    }
    finally {
      Disposer.dispose(disposable)
    }
  }
}

/** Like two entries in `Settings | Languages & Frameworks | LSP Servers` that match the same file. */
private class TwoServersLspProvider : LspBulkIntegrationProvider {
  override fun fileOpened(project: Project, file: VirtualFile, clientStarter: LspIntegrationProvider.LspClientStarter) {
    clientStarter.ensureClientStarted(FakeLspClientDescriptor(project, presentableName = "A"))
    clientStarter.ensureClientStarted(FakeLspClientDescriptor(project, presentableName = "B"))
  }
}

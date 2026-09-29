package com.intellij.platform.lsp

import com.intellij.openapi.application.EDT
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.common.FakeLspClientDescriptor
import com.intellij.platform.lsp.common.FakeLspIntegrationProvider
import com.intellij.platform.lsp.common.fakeLspIntegrationFixture
import com.intellij.platform.lsp.testFramework.awaitFileOpenedByLspServer
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test


@TestApplication
internal class LspClientTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  @Suppress("unused")
  private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture()

  @Test
  fun `module initialization`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val clients0 = LspClientManager.getInstance(project).getClients(FakeLspIntegrationProvider::class.java)
    Assertions.assertTrue(clients0.isEmpty(), "No LSP clients should exist initially")
  }

  @Test
  fun `client initialization`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    codeInsightFixture.configureByText("test.txt", "hello world")
    awaitFileOpenedByLspServer(project, codeInsightFixture.file.virtualFile)
    val clients = LspClientManager.getInstance(project).getClients(FakeLspIntegrationProvider::class.java)
    Assertions.assertTrue(clients.isNotEmpty(), "LSP client should be started after the file is opened")
  }

  @Test
  fun `server received initialized notification`() = timeoutRunBlocking {
    codeInsightFixture.configureByText("test.txt", "hello world")
    awaitFileOpenedByLspServer(project, codeInsightFixture.file.virtualFile)
    val clients = LspClientManager.getInstance(project).getClients(FakeLspIntegrationProvider::class.java)
    val descriptor = clients.first().descriptor as FakeLspClientDescriptor
    Assertions.assertTrue(descriptor.server.initialized, "FakeServer should have received 'initialized' notification")
  }
}
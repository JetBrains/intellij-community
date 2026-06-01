package com.intellij.platform.lsp

import com.intellij.openapi.application.EDT
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.common.FakeLspServerDescriptor
import com.intellij.platform.lsp.common.FakeLspServerSupportProvider
import com.intellij.platform.lsp.common.fakeLspServerProviderFixture
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
internal class LspServerTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  @Suppress("unused")
  private val fakeLspServerProvider by projectFixture.fakeLspServerProviderFixture()

  @Test
  fun `module initialization`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val servers0 = LspServerManager.getInstance(project).getServersForProvider(FakeLspServerSupportProvider::class.java)
    Assertions.assertTrue(servers0.isEmpty(), "No LSP servers should exist initially")
  }

  @Test
  fun `server initialization`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    codeInsightFixture.configureByText("test.txt", "hello world")
    awaitFileOpenedByLspServer(project, codeInsightFixture.file.virtualFile)
    val servers = LspServerManager.getInstance(project).getServersForProvider(FakeLspServerSupportProvider::class.java)
    Assertions.assertTrue(servers.isNotEmpty(), "LSP server should be started after the file is opened")
  }

  @Test
  fun `server received initialized notification`() = timeoutRunBlocking {
    codeInsightFixture.configureByText("test.txt", "hello world")
    awaitFileOpenedByLspServer(project, codeInsightFixture.file.virtualFile)
    val servers = LspServerManager.getInstance(project).getServersForProvider(FakeLspServerSupportProvider::class.java)
    val descriptor = servers.first().descriptor as FakeLspServerDescriptor
    Assertions.assertTrue(descriptor.server.initialized, "FakeServer should have received 'initialized' notification")
  }
}
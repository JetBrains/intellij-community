package com.intellij.platform.lsp

import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.idea.TestFor
import com.intellij.openapi.application.EDT
import com.intellij.platform.lsp.api.LspCommunicationChannel
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.lsp.common.assertContentType
import com.intellij.platform.lsp.common.awaitConsoleText
import com.intellij.platform.lsp.common.configureServerSession
import com.intellij.platform.lsp.common.stopFakeServers
import com.intellij.platform.lsp.common.fakeLspIntegrationFixture
import com.intellij.platform.lsp.impl.serviceView.LspServiceViewContributor
import com.intellij.platform.lsp.impl.serviceView.LspServiceViewSupport
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

/** A server that talks LSP over a socket. Its stdout is free, so the console shows it together with its stderr. */
@TestApplication
internal class LspServiceViewSocketTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  @Suppress("unused")
  private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture(useSocket = true)

  @Test
  @TestFor(issues = ["IJPL-257420"])
  fun `server process output is printed to the console`() = timeoutRunBlocking {
    val virtualFile = codeInsightFixture.configureByText("test.txt", "hello world").virtualFile
    val serverSession = configureServerSession(project, virtualFile)
    val lspClient = LspServiceViewContributor().getServices(project).single()
    assertTrue(lspClient.descriptor.lspCommunicationChannel is LspCommunicationChannel.Socket, "The server should talk LSP over a socket")
    val console = requireNotNull(LspServiceViewSupport.getInstance(project).getOrCreateConsole(lspClient)) {
      "A console should exist for a tracked LSP client"
    }

    serverSession.writeStandardOutput("INFO stdout-marker\n")
    serverSession.writeErrorOutput("ERROR stderr-marker\n")
    serverSession.writeErrorOutput("ERROR tail-marker")
    awaitConsoleText(console, listOf("STDOUT INFO stdout-marker", "STDERR ERROR stderr-marker"))

    serverSession.terminateProcess()
    val consoleText = awaitConsoleText(console, listOf("STDERR ERROR tail-marker"))
    assertEquals(1, consoleText.lines().count { "tail-marker" in it }, "The tail should be printed once, got:\n$consoleText")
    while (lspClient.state != LspServerState.ShutdownUnexpectedly) {
      delay(50.milliseconds)
    }
    withContext(Dispatchers.EDT) {
      val consoleView = console.getConsoleViewForTests()
      assertContentType(consoleView, "STDOUT INFO stdout-marker", ConsoleViewContentType.NORMAL_OUTPUT)
      assertContentType(consoleView, "STDERR ERROR stderr-marker", ConsoleViewContentType.ERROR_OUTPUT)
    }

    stopFakeServers(project)
  }
}

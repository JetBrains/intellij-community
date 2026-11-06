package com.intellij.platform.lsp

import com.intellij.idea.TestFor
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspClientManagerListener
import com.intellij.platform.lsp.common.ExpectationHolder
import com.intellij.platform.lsp.common.FakeLspClientDescriptor
import com.intellij.platform.lsp.common.FakeLspServer
import com.intellij.platform.lsp.common.fakeLspIntegrationFixture
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.testFramework.awaitFileOpenedByLspServer
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import kotlin.time.Duration.Companion.seconds


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

  private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture()

  @Test
  fun `module initialization`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val clients0 = LspClientManager.getInstance(project).getClients(fakeLspIntegration.providerClass)
    Assertions.assertTrue(clients0.isEmpty(), "No LSP clients should exist initially")
  }

  @Test
  fun `client initialization`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    codeInsightFixture.configureByText("test.txt", "hello world")
    awaitFileOpenedByLspServer(project, codeInsightFixture.file.virtualFile)
    val clients = LspClientManager.getInstance(project).getClients(fakeLspIntegration.providerClass)
    Assertions.assertTrue(clients.isNotEmpty(), "LSP client should be started after the file is opened")
  }

  @Test
  fun `server received initialized notification`() = timeoutRunBlocking {
    codeInsightFixture.configureByText("test.txt", "hello world")
    awaitFileOpenedByLspServer(project, codeInsightFixture.file.virtualFile)
    val descriptor = fakeLspIntegration.client.descriptor as FakeLspClientDescriptor
    Assertions.assertTrue(descriptor.server.initialized, "FakeServer should have received 'initialized' notification")
  }

  @Test
  @TestFor(issues = ["PY-93563"])
  fun `a stop before the process start leaves no server process running`() = timeoutRunBlocking {
    val descriptor = ProcessRecordingDescriptor(project)
    val client = LspClientImpl(fakeLspIntegration.providerClass, descriptor, object : LspClientManagerListener {})

    // The client starts its process on a pooled thread, under its connector lock. Holding the lock here makes the stop come
    // first, as in an `@AfterEach` stop right after an action that opened a file. The lock is reentrant, and this thread holds
    // no read lock, so the stop runs here at once. The test creates the client itself: the manager creates and starts it in one
    // write action, so no caller of the manager can take the lock between the two.
    client.holdingConnectorLock {
      client.start()
      client.ensureServerStopped(explicitStop = true) {}
    }

    // No event tells that a process start did not happen, so the test gives the pooled thread time for one.
    val server = withTimeoutOrNull(2.seconds) { descriptor.serverStarted.await() } ?: return@timeoutRunBlocking
    server.destroy()
    fail("a client stopped before its process start must not start the process")
  }

  private class ProcessRecordingDescriptor(project: Project) : FakeLspClientDescriptor(project, presentableName = "FakeLspServerStoppedAtStart") {
    val serverStarted = CompletableDeferred<FakeLspServer>()

    override fun createServer(expectations: ExpectationHolder): FakeLspServer =
      super.createServer(expectations).also { serverStarted.complete(it) }
  }
}

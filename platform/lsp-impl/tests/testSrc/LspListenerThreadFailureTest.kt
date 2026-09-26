package com.intellij.platform.lsp

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.Lsp4jClient
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspServerNotificationsHandler
import com.intellij.platform.lsp.api.LspServerState
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
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds


@TestApplication
internal class LspListenerThreadFailureTest {
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
    createLsp4jClient = { handler -> Lsp4jClient(ThrowingPublishDiagnosticsHandler(handler)) },
  )

  private class NotificationHandlerError : Error("Error in the publishDiagnostics handler")

  private class ThrowingPublishDiagnosticsHandler(delegate: LspServerNotificationsHandler) : LspServerNotificationsHandler by delegate {
    override fun publishDiagnostics(params: PublishDiagnosticsParams) {
      throw NotificationHandlerError()
    }
  }

  @Test
  fun `failing notification handler tears the server down without waiting for a shutdown response`() = timeoutRunBlocking(30.seconds) {
    val virtualFile: VirtualFile = codeInsightFixture.configureByText("diag.txt", "hello world").virtualFile
    val serverSession = configureServerSession(project, virtualFile)
    val fileUri = serverSession.fileUri(virtualFile)

    val lspClient = LspClientManager.getInstance(project).getClients(FakeLspServerSupportProvider::class.java).first()
    val fakeServerProcess = (lspClient.descriptor as FakeLspServerDescriptor).server

    // The handler throws on the listener thread, so the loop dies and the server-to-IDE channel is dead.
    serverSession.sendNotification(serverSession.PUBLISH_DIAGNOSTICS) {
      PublishDiagnosticsParams(fileUri, emptyList())
    }

    // waitFor() returns once the IDE stops the fake server process. With the fix that happens right after the
    // listener loop dies. Without the fix, teardown first calls shutdown().get(10s) on the dead listener
    // thread, so the process is stopped only after that whole timeout elapses — the 8s bound catches that.
    //
    // The real thing to test is that no thread stays blocked. Checking that the IDE disconnects from the
    // server faster than a timed-out shutdown would is a good enough stand-in here.
    withTimeout(8.seconds) {
      runInterruptible(Dispatchers.IO) { fakeServerProcess.waitFor() }
    }

    assertEquals(LspServerState.ShutdownUnexpectedly, lspClient.state,
                 "A failing notification handler must be treated as an unexpected server stop")
  }
}
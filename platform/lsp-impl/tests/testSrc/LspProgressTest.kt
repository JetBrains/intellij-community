package com.intellij.platform.lsp

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.lsp.common.FakeLspServerSupportProvider
import com.intellij.platform.lsp.common.configureServerSession
import com.intellij.platform.lsp.common.fakeLspServerProviderFixture
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.eclipse.lsp4j.ProgressParams
import org.eclipse.lsp4j.WorkDoneProgressBegin
import org.eclipse.lsp4j.WorkDoneProgressEnd
import org.eclipse.lsp4j.WorkDoneProgressNotification
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

@TestApplication
internal class LspProgressTest {
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

  private fun beginParams(token: String, title: String): ProgressParams =
    ProgressParams(
      Either.forLeft(token),
      Either.forLeft<WorkDoneProgressNotification, Any>(WorkDoneProgressBegin().apply { this.title = title }),
    )

  private fun endParams(token: String): ProgressParams =
    ProgressParams(
      Either.forLeft(token),
      Either.forLeft<WorkDoneProgressNotification, Any>(WorkDoneProgressEnd()),
    )

  @Test
  fun `workDoneProgress End stops the progress job started by Begin`() = timeoutRunBlocking(30.seconds) {
    val virtualFile: VirtualFile = codeInsightFixture.configureByText("progress-sanity.txt", "hello").virtualFile
    val serverSession = configureServerSession(project, virtualFile)

    val managerJob = LspClientManagerImpl.getInstanceImpl(project).cs.coroutineContext.job
    val jobsBefore = managerJob.children.toSet()

    serverSession.sendNotification(serverSession.PROGRESS) { beginParams("sanity-token", "Indexing") }

    // Begin launches a progress job into the manager scope; it polls its state every 100 ms until End arrives
    val progressJob: Job = withTimeout(10.seconds) {
      var job: Job? = null
      while (job == null) {
        job = (managerJob.children.toSet() - jobsBefore).firstOrNull { it.isActive }
        if (job == null) delay(50)
      }
      job
    }

    serverSession.sendNotification(serverSession.PROGRESS) { endParams("sanity-token") }

    // join() returns only when End stops the progress job; a hang here means End does not stop it
    progressJob.join()
  }

  @Test
  fun `workDoneProgress Begin after the client stopped must not start a progress job`() = timeoutRunBlocking(60.seconds) {
    val virtualFile: VirtualFile = codeInsightFixture.configureByText("progress-leak.txt", "hello").virtualFile
    configureServerSession(project, virtualFile)

    val manager = LspClientManagerImpl.getInstanceImpl(project)
    // Get the client and its handler before the stop: an explicit stop removes the client from the manager
    val lspClient = manager.getClients(FakeLspServerSupportProvider::class.java).first()
    val handler = lspClient.serverNotificationsHandler

    withContext(Dispatchers.Default) { manager.stopRunningServer(lspClient) }
    assertEquals(LspServerState.ShutdownNormally, lspClient.state)

    val managerJob = manager.cs.coroutineContext.job
    val jobsBefore = managerJob.children.toSet()

    // Models a `$/progress` Begin that is already on the lsp4j listener thread when the stop lands
    handler.notifyProgress(beginParams("leak-token", "Indexing"))

    val newJobs = (managerJob.children.toSet() - jobsBefore).toList()
    try {
      val allCompleted = withTimeoutOrNull(3.seconds) { newJobs.forEach { it.join() } }
      assertNotNull(allCompleted) {
        "Leak: a WorkDoneProgressBegin delivered after the LSP client stopped started a progress job that never completes. " +
        "cancelAllProgress() has already run and WorkDoneProgressEnd can never arrive, so nothing stops the job. " +
        "Leaked jobs: $newJobs"
      }
    }
    finally {
      // Do not leave the leaked poll loop spinning after a red run
      newJobs.forEach { it.cancel() }
    }
  }
}

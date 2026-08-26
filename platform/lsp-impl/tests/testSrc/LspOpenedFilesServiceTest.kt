package com.intellij.platform.lsp

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManagerListener
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.platform.lsp.common.FakeLspServerDescriptor
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.platform.lsp.impl.documentSync.LspOpenedFilesService
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.extensionPointFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.nio.file.Files
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@TestApplication
internal class LspOpenedFilesServiceTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture
    private val tempDir by tempDirFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)
  }

  private val provider by extensionPointFixture(LspIntegrationProvider.EP_NAME) { CoveredFileLspProvider() }

  @Test
  fun `a file covered by a running server does not suppress the start for the next file`() = timeoutRunBlocking(2.minutes) {
    val covered = createLocalFile("covered.txt")
    val other = createLocalFile("other.txt")
    val manager = LspClientManagerImpl.getInstanceImpl(project)
    val service = LspOpenedFilesService.getInstance(project)

    awaitRunningClient(manager) { service.processOpenedFiles(listOf(covered)) }

    service.processOpenedFiles(listOf(covered, other))
    val openedFile = try {
      withTimeout(30.seconds) { provider.uncoveredFileOpened.await() }
    }
    catch (e: TimeoutCancellationException) {
      fail { "fileOpened not called for the file the running server does not cover: $e" }
    }
    assertEquals(other, openedFile)

    stopClientsAndWait(manager)
  }

  private fun createLocalFile(name: String): VirtualFile {
    val path = tempDir.resolve(name)
    Files.writeString(path, "content")
    // the file is created behind the VFS's back: refresh brings it in
    val file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path.toString().replace('\\', '/'))
    assertNotNull(file, name)
    return file!!
  }

  private suspend fun stopClientsAndWait(manager: LspClientManagerImpl) {
    val removed = CompletableDeferred<Unit>()
    val disposable = Disposer.newDisposable("LspOpenedFilesServiceTest")
    try {
      manager.addListener(object : LspClientManagerListener {
        override fun clientRemoved(lspClient: LspClient) {
          removed.complete(Unit)
        }
      }, disposable, false)
      manager.stopClients(CoveredFileLspProvider::class.java)
      removed.await()
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  private suspend fun awaitRunningClient(manager: LspClientManagerImpl, action: () -> Unit) {
    val running = CompletableDeferred<Unit>()
    val disposable = Disposer.newDisposable("LspOpenedFilesServiceTest")
    try {
      manager.addListener(object : LspClientManagerListener {
        override fun serverStateChanged(lspClient: LspClient) {
          if (lspClient.state == LspServerState.Running) running.complete(Unit)
        }
      }, disposable, false)
      action()
      running.await()
    }
    finally {
      Disposer.dispose(disposable)
    }
  }
}

/** Starts a server that covers only `covered.txt`, and reports a [fileOpened] call for any other file. */
private class CoveredFileLspProvider : LspIntegrationProvider {
  val uncoveredFileOpened = CompletableDeferred<VirtualFile>()

  override fun fileOpened(project: Project, file: VirtualFile, clientStarter: LspIntegrationProvider.LspClientStarter) {
    clientStarter.ensureClientStarted(
      FakeLspServerDescriptor(project, LspCustomization(), null, null, supportedFilePredicate = { it.name == "covered.txt" }))
    if (file.name != "covered.txt") uncoveredFileOpened.complete(file)
  }
}

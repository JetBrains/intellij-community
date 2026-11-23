package com.intellij.platform.lsp

import com.intellij.idea.TestFor
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManagerListener
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.lsp.common.FakeLspClientDescriptor
import com.intellij.platform.lsp.common.FakeLspConfig
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.platform.lsp.impl.documentSync.LspOpenedFilesService
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.extensionPointFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@TestApplication
internal class LspOpenedFilesServiceTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture
    private val tempDir by tempDirFixture

    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)
    private val module by moduleFixture
  }

  private val provider by extensionPointFixture(LspIntegrationProvider.EP_NAME) { CoveredFileLspProvider() }
  private val outsideDir by tempPathFixture()

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

  @Test
  fun `a start request captured before an explicit stop does not revive a client`() = timeoutRunBlocking(2.minutes) {
    val covered = createLocalFile("covered.txt")
    val manager = LspClientManagerImpl.getInstanceImpl(project)
    val service = LspOpenedFilesService.getInstance(project)
    val providerClass = provider.javaClass

    awaitRunningClient(manager) { service.processOpenedFiles(listOf(covered)) }
    val descriptor = manager.getClients(providerClass).single().descriptor
    val staleRequestStamp = manager.startRequestStamp()

    stopClientsAndWait(manager)

    manager.ensureStarted(providerClass, descriptor, staleRequestStamp)!!.join()
    assertTrue(manager.getClients(providerClass).isEmpty(), "the stale start request must not revive a client")

    // an explicit request with no captured generation still starts a client after the stop
    awaitRunningClient(manager) { manager.ensureClientStarted(providerClass, descriptor) }
    stopClientsAndWait(manager)
  }

  @Test
  fun `a start request captured before a single-client stop does not revive the client`() = timeoutRunBlocking(2.minutes) {
    val covered = createLocalFile("covered.txt")
    val manager = LspClientManagerImpl.getInstanceImpl(project)
    val service = LspOpenedFilesService.getInstance(project)
    val providerClass = provider.javaClass

    awaitRunningClient(manager) { service.processOpenedFiles(listOf(covered)) }
    val client = manager.getClients(providerClass).single()
    val staleRequestStamp = manager.startRequestStamp()

    stopAndWait(manager) { manager.stopRunningServer(client) }

    manager.ensureStarted(providerClass, client.descriptor, staleRequestStamp)!!.join()
    assertTrue(manager.getClients(providerClass).isEmpty(), "the stale start request must not revive the client")
  }

  @Test
  fun `a single-client stop does not suppress a queued start of a sibling client`() = timeoutRunBlocking(2.minutes) {
    val covered = createLocalFile("covered.txt")
    val manager = LspClientManagerImpl.getInstanceImpl(project)
    val service = LspOpenedFilesService.getInstance(project)
    val providerClass = provider.javaClass

    awaitRunningClient(manager) { service.processOpenedFiles(listOf(covered)) }
    val client = manager.getClients(providerClass).single()
    val siblingDescriptor = FakeLspClientDescriptor(project, presentableName = "FakeLspServerSibling")
    val staleRequestStamp = manager.startRequestStamp()

    stopAndWait(manager) { manager.stopRunningServer(client) }

    try {
      withTimeout(30.seconds) {
        awaitRunningClient(manager) { manager.ensureStarted(providerClass, siblingDescriptor, staleRequestStamp) }
      }
    }
    catch (e: TimeoutCancellationException) {
      fail { "the sibling client must start, the stop covered only the other client: $e" }
    }
    assertEquals(listOf(siblingDescriptor), manager.getClients(providerClass).map { it.descriptor },
                 "only the sibling client must run, the stale start request must not revive the stopped client")

    stopClientsAndWait(manager)
  }

  @Test
  fun `a report after a stop starts a new client for the same file`() = timeoutRunBlocking(2.minutes) {
    val covered = createLocalFile("covered.txt")
    val manager = LspClientManagerImpl.getInstanceImpl(project)
    val service = LspOpenedFilesService.getInstance(project)

    awaitRunningClient(manager) { service.processOpenedFiles(listOf(covered)) }
    stopClientsAndWait(manager)

    awaitRunningClient(manager) { service.processOpenedFiles(listOf(covered)) }
    stopClientsAndWait(manager)
  }

  @Test
  @TestFor(issues = ["IJPL-262657"])
  fun `a root change reports no unsaved file outside the project content`() = timeoutRunBlocking(2.minutes) {
    val marker = createLocalFile("unsaved-marker.txt")
    val outside = createLocalFile("unsaved-outside.txt", outsideDir)
    val excluded = createLocalFile("roots-change/excluded.txt").parent
    val manager = LspClientManagerImpl.getInstanceImpl(project)
    val documents = listOf(marker, outside).map { file -> readAction { FileDocumentManager.getInstance().getDocument(file)!! } }
    try {
      edtWriteAction { documents.forEach { it.setText(it.text + " changed") } }

      // the marker starts the client
      awaitRunningClient(manager) { edtWriteAction { PsiTestUtil.addExcludedRoot(module, excluded) } }
      val openedFiles = provider.openedFiles.value
      assertTrue(marker in openedFiles, "fileOpened must get the unsaved file of the project: $openedFiles")
      // one batch reports the marker and the outside file, so a report of the outside file would be here by now
      assertFalse(outside in openedFiles, "fileOpened must not get an unsaved file outside the project content: $openedFiles")
    }
    finally {
      edtWriteAction {
        PsiTestUtil.removeExcludedRoot(module, excluded)
        documents.forEach { FileDocumentManager.getInstance().saveDocument(it) }
      }
      stopClientsAndWait(manager)
    }
  }

  private fun createLocalFile(name: String, dir: Path = tempDir): VirtualFile {
    val path = dir.resolve(name)
    Files.createDirectories(path.parent)
    Files.writeString(path, "content")
    // the file is created behind the VFS's back: refresh brings it in
    val file = StandardFileSystems.local().refreshAndFindFileByPath(path.toString().replace('\\', '/'))
    assertNotNull(file, name)
    return file!!
  }

  private suspend fun stopClientsAndWait(manager: LspClientManagerImpl): Unit =
    stopAndWait(manager) { manager.stopClients(CoveredFileLspProvider::class.java) }

  private suspend fun stopAndWait(manager: LspClientManagerImpl, stop: () -> Unit) {
    val removed = CompletableDeferred<Unit>()
    val disposable = Disposer.newDisposable("LspOpenedFilesServiceTest")
    try {
      manager.addListener(object : LspClientManagerListener {
        override fun clientRemoved(lspClient: LspClient) {
          removed.complete(Unit)
        }
      }, disposable, false)
      stop()
      removed.await()
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  private suspend fun awaitRunningClient(manager: LspClientManagerImpl, action: suspend () -> Unit) {
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
  val openedFiles = MutableStateFlow<List<VirtualFile>>(emptyList())

  override fun fileOpened(project: Project, file: VirtualFile, clientStarter: LspIntegrationProvider.LspClientStarter) {
    openedFiles.update { it + file }
    clientStarter.ensureClientStarted(
      FakeLspClientDescriptor(project, FakeLspConfig(isSupportedFile = { it.name == "covered.txt" })))
    if (file.name != "covered.txt") uncoveredFileOpened.complete(file)
  }
}

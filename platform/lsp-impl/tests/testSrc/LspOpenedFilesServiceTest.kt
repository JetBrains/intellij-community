package com.intellij.platform.lsp

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.platform.lsp.common.FakeLspClientDescriptor
import com.intellij.platform.lsp.common.FakeLspConfig
import com.intellij.platform.lsp.common.awaitRunningClient
import com.intellij.platform.lsp.common.stopAndWait
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
import org.junit.jupiter.api.Assertions.assertTrue
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
    assertStaleStartRequestDoesNotRevive(stoppedBeforeCapture = false)
  }

  @Test
  fun `a start request captured before a stop of a stopped client does not revive the client`() = timeoutRunBlocking(2.minutes) {
    assertStaleStartRequestDoesNotRevive(stoppedBeforeCapture = true)
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

    stopAndWait(manager, { it === client }) { manager.stopRunningServer(client) }

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

  private suspend fun assertStaleStartRequestDoesNotRevive(stoppedBeforeCapture: Boolean) {
    val covered = createLocalFile("covered.txt")
    val manager = LspClientManagerImpl.getInstanceImpl(project)
    val service = LspOpenedFilesService.getInstance(project)
    val providerClass = provider.javaClass

    awaitRunningClient(manager) { service.processOpenedFiles(listOf(covered)) }
    val client = manager.getClients(providerClass).single()
    if (stoppedBeforeCapture) stopAndWait(manager, { it === client }) { manager.stopRunningServer(client) }
    val staleRequestStamp = manager.startRequestStamp()

    if (stoppedBeforeCapture) manager.stopRunningServer(client)
    else stopAndWait(manager, { it === client }) { manager.stopRunningServer(client) }

    manager.ensureStarted(providerClass, client.descriptor, staleRequestStamp)!!.join()
    assertTrue(manager.getClients(providerClass).isEmpty(), "the stale start request must not revive the client")
  }

  private fun createLocalFile(name: String): VirtualFile {
    val path = tempDir.resolve(name)
    Files.writeString(path, "content")
    // the file is created behind the VFS's back: refresh brings it in
    val file = StandardFileSystems.local().refreshAndFindFileByPath(path.toString().replace('\\', '/'))
    assertNotNull(file, name)
    return file!!
  }

  private suspend fun stopClientsAndWait(manager: LspClientManagerImpl): Unit =
    stopAndWait(manager) { manager.stopClients(CoveredFileLspProvider::class.java) }
}

/** Starts a server that covers only `covered.txt`, and reports a [fileOpened] call for any other file. */
private class CoveredFileLspProvider : LspIntegrationProvider {
  val uncoveredFileOpened = CompletableDeferred<VirtualFile>()

  override fun fileOpened(project: Project, file: VirtualFile, clientStarter: LspIntegrationProvider.LspClientStarter) {
    clientStarter.ensureClientStarted(
      FakeLspClientDescriptor(project, FakeLspConfig(isSupportedFile = { it.name == "covered.txt" })))
    if (file.name != "covered.txt") uncoveredFileOpened.complete(file)
  }
}

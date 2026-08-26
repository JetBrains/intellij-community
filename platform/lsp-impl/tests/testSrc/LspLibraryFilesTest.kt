package com.intellij.platform.lsp

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManagerListener
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.lsp.common.FakeLspServerSupportProvider
import com.intellij.platform.lsp.common.configureServerSession
import com.intellij.platform.lsp.common.currentServerSession
import com.intellij.platform.lsp.common.fakeLspServerProviderFixture
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.platform.lsp.impl.features.documentation.LspDocumentationTargetProvider
import com.intellij.platform.lsp.impl.features.navigation.LspLibraryFiles
import com.intellij.platform.lsp.impl.getServerId
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.psi.PsiManager
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.ExecuteCommandOptions
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.MarkupKind
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.util.Collections
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@TestApplication
internal class LspLibraryFilesTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture
    private val tempDir by tempDirFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  @Suppress("unused")
  private val fakeLspServerProvider by projectFixture.fakeLspServerProviderFixture(
    configureServerCapabilities = {
      hoverProvider = Either.forLeft(true)
      executeCommandProvider = ExecuteCommandOptions(listOf("decompile"))
    },
    isSupportedLibraryFile = { it.name == "Served.txt" },
  )

  @Test
  fun `decompiled file keeps the original uri and the producing client`() = timeoutRunBlocking {
    val virtualFile = codeInsightFixture.configureByText("test.txt", "hello").virtualFile
    val serverSession = configureServerSession(project, virtualFile)
    val manager = LspClientManagerImpl.getInstanceImpl(project)
    val client = manager.getRunningClients().single()

    val uri = "jar:///lib/foo.jar!/com/foo/Bar.class"
    serverSession.expectRequest(serverSession.EXECUTE_COMMAND, { it.command == "decompile" }) {
      mapOf("code" to "decompiled text", "language" to "TEXT")
    }
    serverSession.expectNotification(serverSession.DID_OPEN) {
      it.textDocument.uri == uri && it.textDocument.text == "decompiled text" &&
      it.textDocument.languageId == "TEXT" && it.textDocument.version == 0
    }
    val decompiled = client.libraryFiles.getOrDecompile(uri)
    serverSession.awaitExpected()

    assertNotNull(decompiled)
    assertEquals("Bar.class", decompiled!!.name)
    assertEquals("decompiled text", VfsUtilCore.loadText(decompiled))
    assertFalse(decompiled.isWritable)
    assertEquals(uri, client.getDocumentIdentifier(decompiled).uri)
    assertSame(decompiled, client.libraryFiles.getOrDecompile(uri))

    val clients = readAction { manager.getClientsForFileRequests(decompiled) }
    assertEquals(listOf(client), clients.toList())
  }

  @Test
  fun `hover in a decompiled file uses the original uri`() = timeoutRunBlocking {
    val virtualFile = codeInsightFixture.configureByText("test.txt", "hello").virtualFile
    val serverSession = configureServerSession(project, virtualFile)
    val client = LspClientManagerImpl.getInstanceImpl(project).getRunningClients().single()

    val uri = "jar:///lib/foo.jar!/com/foo/Bar.class"
    serverSession.expectRequest(serverSession.EXECUTE_COMMAND, { it.command == "decompile" }) {
      mapOf("code" to "decompiled text")
    }
    val decompiled = client.libraryFiles.getOrDecompile(uri)
    serverSession.awaitExpected()
    assertNotNull(decompiled)

    serverSession.expectRequest(serverSession.HOVER, { it.textDocument.uri == uri }) {
      Hover(MarkupContent(MarkupKind.PLAINTEXT, "decompiled doc"))
    }
    val targets = readAction {
      val psiFile = PsiManager.getInstance(project).findFile(decompiled!!)
      assertNotNull(psiFile)
      LspDocumentationTargetProvider().documentationTargets(psiFile!!, 0)
    }
    serverSession.awaitExpected()

    assertEquals(1, targets.size)
  }

  @Test
  fun `a stopped client serves no decompiled files`() = timeoutRunBlocking {
    val virtualFile = codeInsightFixture.configureByText("test.txt", "hello").virtualFile
    val serverSession = configureServerSession(project, virtualFile)
    val manager = LspClientManagerImpl.getInstanceImpl(project)
    val client = manager.getRunningClients().single()

    serverSession.expectRequest(serverSession.EXECUTE_COMMAND, { it.command == "decompile" }) {
      mapOf("code" to "decompiled text")
    }
    val decompiled = client.libraryFiles.getOrDecompile("jrt://jdk/java.base/java/lang/String.class")
    serverSession.awaitExpected()
    assertNotNull(decompiled)

    stopClientsAndWait(manager)

    val clients = readAction { manager.getClientsForFileRequests(decompiled!!) }
    assertTrue(clients.isEmpty(), "A stopped client must not serve its decompiled files: $clients")
  }

  @Test
  fun `a restarted client adopts a decompiled file`() = timeoutRunBlocking {
    val virtualFile = codeInsightFixture.configureByText("test.txt", "hello").virtualFile
    val serverSession = configureServerSession(project, virtualFile)
    val manager = LspClientManagerImpl.getInstanceImpl(project)
    val client = manager.getRunningClients().single()

    val uri = "jar:///lib/foo.jar!/com/foo/Bar.class"
    serverSession.expectRequest(serverSession.EXECUTE_COMMAND, { it.command == "decompile" }) {
      mapOf("code" to "decompiled text", "language" to "TEXT")
    }
    val decompiled = client.libraryFiles.getOrDecompile(uri)
    serverSession.awaitExpected()
    assertNotNull(decompiled)

    stopClientsAndWait(manager)
    manager.startClientsIfNeeded(FakeLspServerSupportProvider::class.java)
    val restartedSession = configureServerSession(project, virtualFile)
    val restarted = manager.getRunningClients().single()
    assertNotSame(client, restarted)

    restartedSession.expectNotification(restartedSession.DID_OPEN) {
      it.textDocument.uri == uri && it.textDocument.text == "decompiled text" &&
      it.textDocument.languageId == "TEXT" && it.textDocument.version == 0
    }
    val clients = readAction { manager.getClientsForFileRequests(decompiled!!) }
    restartedSession.awaitExpected()

    assertEquals(listOf(restarted), clients.toList())
    assertSame(decompiled, restarted.libraryFiles.getOrDecompile(uri))
  }

  @Test
  fun `a restart serves a decompiled file when no local file is open`() = timeoutRunBlocking {
    val virtualFile = codeInsightFixture.configureByText("test.txt", "hello").virtualFile
    val serverSession = configureServerSession(project, virtualFile)
    val manager = LspClientManagerImpl.getInstanceImpl(project)
    val client = manager.getRunningClients().single()

    val uri = "jar:///lib/foo.jar!/com/foo/Bar.class"
    serverSession.expectRequest(serverSession.EXECUTE_COMMAND, { it.command == "decompile" }) {
      mapOf("code" to "decompiled text", "language" to "TEXT")
    }
    val decompiled = client.libraryFiles.getOrDecompile(uri)
    serverSession.awaitExpected()
    assertNotNull(decompiled)

    // only the decompiled tab remains: the open-editors scan cannot revive the server, the restart must reuse the descriptor
    withContext(Dispatchers.EDT) { FileEditorManager.getInstance(project).closeFile(virtualFile) }
    restartClientAndWait(manager, client)
    val restarted = manager.getRunningClients().single()
    assertNotSame(client, restarted)

    val restartedSession = currentServerSession(project)
    restartedSession.expectNotification(restartedSession.DID_OPEN) {
      it.textDocument.uri == uri && it.textDocument.text == "decompiled text" &&
      it.textDocument.languageId == "TEXT" && it.textDocument.version == 0
    }
    val clients = readAction { manager.getClientsForFileRequests(decompiled!!) }
    restartedSession.awaitExpected()

    assertEquals(listOf(restarted), clients.toList())
  }

  @Test
  fun `a client does not adopt a decompiled file from another provider's server`() = timeoutRunBlocking {
    val virtualFile = codeInsightFixture.configureByText("test.txt", "hello").virtualFile
    val serverSession = configureServerSession(project, virtualFile)
    val manager = LspClientManagerImpl.getInstanceImpl(project)
    val client = manager.getRunningClients().single()

    serverSession.expectRequest(serverSession.EXECUTE_COMMAND, { it.command == "decompile" }) {
      mapOf("code" to "decompiled text")
    }
    val decompiled = client.libraryFiles.getOrDecompile("jar:///lib/foo.jar!/com/foo/Bar.class")
    serverSession.awaitExpected()
    assertNotNull(decompiled)
    decompiled!!.putUserData(LspLibraryFiles.DECOMPILED_BY_SERVER_ID, getServerId(AnotherLspProvider::class.java, client.descriptor))

    stopClientsAndWait(manager)
    manager.startClientsIfNeeded(FakeLspServerSupportProvider::class.java)
    configureServerSession(project, virtualFile)

    val clients = readAction { manager.getClientsForFileRequests(decompiled) }
    assertTrue(clients.isEmpty(), "Another provider's server produced the file, so no client must adopt it: $clients")
  }

  @Test
  fun `requests inside a navigated jar file go to the producing client`() = timeoutRunBlocking {
    val virtualFile = codeInsightFixture.configureByText("test.txt", "hello").virtualFile
    configureServerSession(project, virtualFile)
    val manager = LspClientManagerImpl.getInstanceImpl(project)
    val client = manager.getRunningClients().single()

    val jarEntry = createJarEntry("lib.jar", "com/foo/Bar.txt")

    val before = readAction { manager.getClientsForFileRequests(jarEntry) }
    assertTrue(before.isEmpty(), "The client does not declare library file support for the entry, so nothing routes there yet: $before")

    val target = client.libraryFiles.findTargetFile(client.descriptor.getFileUri(jarEntry))
    assertEquals(jarEntry, target)

    val after = readAction { manager.getClientsForFileRequests(jarEntry) }
    assertEquals(listOf(client), after.toList())
  }

  @Test
  fun `a jar file goes to a client that declares library file support`() = timeoutRunBlocking {
    val virtualFile = codeInsightFixture.configureByText("test.txt", "hello").virtualFile
    configureServerSession(project, virtualFile)
    val manager = LspClientManagerImpl.getInstanceImpl(project)
    val client = manager.getRunningClients().single()

    val jarEntry = createJarEntry("served.jar", "com/foo/Served.txt")

    val clients = readAction { manager.getClientsForFileRequests(jarEntry) }
    assertEquals(listOf(client), clients.toList())
  }

  @Test
  fun `a jrt target decompiles without a uri warning`() = timeoutRunBlocking {
    val virtualFile = codeInsightFixture.configureByText("test.txt", "hello").virtualFile
    val serverSession = configureServerSession(project, virtualFile)
    val client = LspClientManagerImpl.getInstanceImpl(project).getRunningClients().single()

    serverSession.expectRequest(serverSession.EXECUTE_COMMAND, { it.command == "decompile" }) {
      mapOf("code" to "decompiled text")
    }
    val warnings = Collections.synchronizedList(mutableListOf<String>())
    val processor = object : LoggedErrorProcessor() {
      override fun processWarn(category: String, message: String, t: Throwable?): Boolean {
        warnings.add(message)
        return true
      }
    }
    val target = LoggedErrorProcessor.executeWith(processor).use {
      client.libraryFiles.findTargetFile("jrt://jdk/java.base/java/lang/String.class")
    }
    serverSession.awaitExpected()

    assertNotNull(target)
    assertEquals("String.class", target!!.name)
    val uriWarnings = warnings.filter { it.contains("URI") }
    assertTrue(uriWarnings.isEmpty(), "A jrt navigation must not warn about the URI: $uriWarnings")
  }

  private fun createJarEntry(jarName: String, entryPath: String): VirtualFile {
    val jarPath = tempDir.resolve(jarName)
    ZipOutputStream(Files.newOutputStream(jarPath)).use { zip ->
      zip.putNextEntry(ZipEntry(entryPath))
      zip.write("class content".toByteArray())
      zip.closeEntry()
    }
    // the jar is created behind the VFS's back: bring the local file in first
    val localJarPath = jarPath.toString().replace('\\', '/')
    assertNotNull(LocalFileSystem.getInstance().refreshAndFindFileByPath(localJarPath), localJarPath)
    val jarVfsPath = "$localJarPath!/$entryPath"
    val jarEntry = JarFileSystem.getInstance().refreshAndFindFileByPath(jarVfsPath)
    assertNotNull(jarEntry, jarVfsPath)
    return jarEntry!!
  }

  private suspend fun restartClientAndWait(manager: LspClientManagerImpl, client: LspClientImpl) {
    val running = CompletableDeferred<Unit>()
    val disposable = Disposer.newDisposable("LspLibraryFilesTest")
    try {
      manager.addListener(object : LspClientManagerListener {
        override fun serverStateChanged(lspClient: LspClient) {
          if (lspClient !== client && lspClient.state == LspServerState.Running) running.complete(Unit)
        }
      }, disposable, false)
      manager.restartClient(client)
      running.await()
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  private suspend fun stopClientsAndWait(manager: LspClientManagerImpl) {
    val removed = CompletableDeferred<Unit>()
    val disposable = Disposer.newDisposable("LspLibraryFilesTest")
    try {
      manager.addListener(object : LspClientManagerListener {
        override fun clientRemoved(lspClient: LspClient) {
          removed.complete(Unit)
        }
      }, disposable, false)
      LspServerManager.getInstance(project).stopServers(FakeLspServerSupportProvider::class.java)
      removed.await()
    }
    finally {
      Disposer.dispose(disposable)
    }
  }
}

/** A provider that shares the descriptor with [FakeLspServerSupportProvider], to prove that the server identity includes the provider. */
private class AnotherLspProvider : LspIntegrationProvider {
  override fun fileOpened(project: Project, file: VirtualFile, clientStarter: LspIntegrationProvider.LspClientStarter) = Unit
}

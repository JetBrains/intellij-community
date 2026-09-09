package com.intellij.platform.lsp

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.StandardFileSystems
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
import com.intellij.platform.lsp.impl.features.navigation.LspDynamicFiles
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
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.MarkupKind
import org.eclipse.lsp4j.TextDocumentContentRefreshParams
import org.eclipse.lsp4j.TextDocumentContentRegistrationOptions
import org.eclipse.lsp4j.TextDocumentContentResult
import org.eclipse.lsp4j.WorkspaceServerCapabilities
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.util.Collections
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@TestApplication
internal class LspDynamicFilesTest {
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
      workspace = WorkspaceServerCapabilities().apply {
        textDocumentContent = TextDocumentContentRegistrationOptions(listOf("jar", "jrt"))
      }
    },
  )

  @Test
  fun `decompiled file keeps the original uri and the producing client`() = timeoutRunBlocking {
    val virtualFile = codeInsightFixture.configureByText("test.txt", "hello").virtualFile
    val serverSession = configureServerSession(project, virtualFile)
    val manager = LspClientManagerImpl.getInstanceImpl(project)
    val client = manager.getRunningClients().single()

    val uri = "jar:///lib/foo.jar!/com/foo/Bar.class"
    serverSession.expectRequest(serverSession.TEXT_DOCUMENT_CONTENT, { it.uri == uri }) {
      TextDocumentContentResult("decompiled text")
    }
    serverSession.expectNotification(serverSession.DID_OPEN) {
      it.textDocument.uri == uri && it.textDocument.text == "decompiled text" &&
      it.textDocument.languageId == "class" && it.textDocument.version == 0
    }
    val decompiled = client.dynamicFiles.getOrRequestContent(uri)
    serverSession.awaitExpected()

    assertNotNull(decompiled)
    assertEquals("Bar.class", decompiled!!.name)
    assertEquals("decompiled text", VfsUtilCore.loadText(decompiled))
    assertFalse(decompiled.isWritable)
    assertEquals(uri, client.descriptor.getFileUri(decompiled))
    assertEquals(uri, client.getDocumentIdentifier(decompiled).uri)
    assertSame(decompiled, client.dynamicFiles.getOrRequestContent(uri))

    val clients = readAction { manager.getClientsForFileRequests(decompiled) }
    assertEquals(listOf(client), clients.toList())
  }

  @Test
  fun `hover in a decompiled file uses the original uri`() = timeoutRunBlocking {
    val virtualFile = codeInsightFixture.configureByText("test.txt", "hello").virtualFile
    val serverSession = configureServerSession(project, virtualFile)
    val client = LspClientManagerImpl.getInstanceImpl(project).getRunningClients().single()

    val uri = "jar:///lib/foo.jar!/com/foo/Bar.class"
    serverSession.expectRequest(serverSession.TEXT_DOCUMENT_CONTENT, { it.uri == uri }) {
      TextDocumentContentResult("decompiled text")
    }
    val decompiled = client.dynamicFiles.getOrRequestContent(uri)
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

    val uri = "jrt://jdk/java.base/java/lang/String.class"
    serverSession.expectRequest(serverSession.TEXT_DOCUMENT_CONTENT, { it.uri == uri }) {
      TextDocumentContentResult("decompiled text")
    }
    val decompiled = client.dynamicFiles.getOrRequestContent(uri)
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
    serverSession.expectRequest(serverSession.TEXT_DOCUMENT_CONTENT, { it.uri == uri }) {
      TextDocumentContentResult("decompiled text")
    }
    val decompiled = client.dynamicFiles.getOrRequestContent(uri)
    serverSession.awaitExpected()
    assertNotNull(decompiled)

    stopClientsAndWait(manager)
    manager.startClientsIfNeeded(FakeLspServerSupportProvider::class.java)
    val restartedSession = configureServerSession(project, virtualFile)
    val restarted = manager.getRunningClients().single()
    assertNotSame(client, restarted)

    restartedSession.expectNotification(restartedSession.DID_OPEN) {
      it.textDocument.uri == uri && it.textDocument.text == "decompiled text" &&
      it.textDocument.languageId == "class" && it.textDocument.version == 0
    }
    val clients = readAction { manager.getClientsForFileRequests(decompiled!!) }
    restartedSession.awaitExpected()

    assertEquals(listOf(restarted), clients.toList())
    assertSame(decompiled, restarted.dynamicFiles.getOrRequestContent(uri))
  }

  @Test
  fun `a restart serves a decompiled file when no local file is open`() = timeoutRunBlocking {
    val virtualFile = codeInsightFixture.configureByText("test.txt", "hello").virtualFile
    val serverSession = configureServerSession(project, virtualFile)
    val manager = LspClientManagerImpl.getInstanceImpl(project)
    val client = manager.getRunningClients().single()

    val uri = "jar:///lib/foo.jar!/com/foo/Bar.class"
    serverSession.expectRequest(serverSession.TEXT_DOCUMENT_CONTENT, { it.uri == uri }) {
      TextDocumentContentResult("decompiled text")
    }
    val decompiled = client.dynamicFiles.getOrRequestContent(uri)
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
      it.textDocument.languageId == "class" && it.textDocument.version == 0
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

    val uri = "jar:///lib/foo.jar!/com/foo/Bar.class"
    serverSession.expectRequest(serverSession.TEXT_DOCUMENT_CONTENT, { it.uri == uri }) {
      TextDocumentContentResult("decompiled text")
    }
    val decompiled = client.dynamicFiles.getOrRequestContent(uri)
    serverSession.awaitExpected()
    assertNotNull(decompiled)
    decompiled!!.putUserData(LspDynamicFiles.CONTENT_BY_SERVER_ID, getServerId(AnotherLspProvider::class.java, client.descriptor))

    stopClientsAndWait(manager)
    manager.startClientsIfNeeded(FakeLspServerSupportProvider::class.java)
    configureServerSession(project, virtualFile)

    val clients = readAction { manager.getClientsForFileRequests(decompiled) }
    assertTrue(clients.isEmpty(), "Another provider's server produced the file, so no client must adopt it: $clients")
  }

  @Test
  fun `a jar target with server content is served by the server, not by the archive file system`() = timeoutRunBlocking {
    val virtualFile = codeInsightFixture.configureByText("test.txt", "hello").virtualFile
    val serverSession = configureServerSession(project, virtualFile)
    val client = LspClientManagerImpl.getInstanceImpl(project).getRunningClients().single()

    val jarEntry = createJarEntry()
    val uri = "jar://${jarEntry.path}"
    serverSession.expectRequest(serverSession.TEXT_DOCUMENT_CONTENT, { it.uri == uri }) {
      TextDocumentContentResult("served text")
    }
    val target = client.dynamicFiles.findTargetFile(uri)
    serverSession.awaitExpected()

    assertNotNull(target)
    assertNotSame(jarEntry, target)
    assertEquals("Bar.txt", target!!.name)
    assertEquals("served text", VfsUtilCore.loadText(target))
  }

  @Test
  fun `a jar target without server content resolves through the descriptor only`() = timeoutRunBlocking {
    val virtualFile = codeInsightFixture.configureByText("test.txt", "hello").virtualFile
    configureServerSession(project, virtualFile)
    val client = LspClientManagerImpl.getInstanceImpl(project).getRunningClients().single()

    val warnings = Collections.synchronizedList(mutableListOf<String>())
    val processor = object : LoggedErrorProcessor() {
      override fun processWarn(category: String, message: String, t: Throwable?): Boolean {
        warnings.add(message)
        return true
      }
    }
    val target = LoggedErrorProcessor.executeWith(processor).use {
      client.dynamicFiles.findTargetFile("zip:///lib/foo.zip!/com/foo/Bar.txt")
    }

    assertNull(target)
    assertTrue(warnings.any { it.contains("Unexpected URI scheme") }, "The descriptor rejects an undeclared scheme: $warnings")
  }

  @Test
  fun `a jrt target decompiles without a uri warning`() = timeoutRunBlocking {
    val virtualFile = codeInsightFixture.configureByText("test.txt", "hello").virtualFile
    val serverSession = configureServerSession(project, virtualFile)
    val client = LspClientManagerImpl.getInstanceImpl(project).getRunningClients().single()

    serverSession.expectRequest(serverSession.TEXT_DOCUMENT_CONTENT, { it.uri.startsWith("jrt:") }) {
      TextDocumentContentResult("decompiled text")
    }
    val warnings = Collections.synchronizedList(mutableListOf<String>())
    val processor = object : LoggedErrorProcessor() {
      override fun processWarn(category: String, message: String, t: Throwable?): Boolean {
        warnings.add(message)
        return true
      }
    }
    val target = LoggedErrorProcessor.executeWith(processor).use {
      client.dynamicFiles.findTargetFile("jrt://jdk/java.base/java/lang/String.class")
    }
    serverSession.awaitExpected()

    assertNotNull(target)
    assertEquals("String.class", target!!.name)
    val uriWarnings = warnings.filter { it.contains("URI") }
    assertTrue(uriWarnings.isEmpty(), "A jrt navigation must not warn about the URI: $uriWarnings")
  }

  @Test
  fun `a content refresh replaces the text of the served file`(): Unit = timeoutRunBlocking {
    val virtualFile = codeInsightFixture.configureByText("test.txt", "hello").virtualFile
    val serverSession = configureServerSession(project, virtualFile)
    val client = LspClientManagerImpl.getInstanceImpl(project).getRunningClients().single()

    val uri = "jar:///lib/foo.jar!/com/foo/Bar.class"
    serverSession.expectRequest(serverSession.TEXT_DOCUMENT_CONTENT, { it.uri == uri }) {
      TextDocumentContentResult("old text")
    }
    val file = client.dynamicFiles.getOrRequestContent(uri)
    serverSession.awaitExpected()
    assertNotNull(file)
    val document = readAction { FileDocumentManager.getInstance().getDocument(file!!) }
    assertNotNull(document)
    assertEquals("old text", readAction { document!!.text })

    var replaced = CompletableDeferred<Unit>()
    val disposable = Disposer.newDisposable("LspDynamicFilesTest.refresh")
    try {
      document!!.addDocumentListener(object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
          replaced.complete(Unit)
        }
      }, disposable)
      // the server keeps the document open since the didOpen, so each replaced text must reach it as a versioned change
      for ((text, version) in listOf("new text" to 1, "newer text" to 2)) {
        replaced = CompletableDeferred()
        serverSession.expectRequest(serverSession.TEXT_DOCUMENT_CONTENT, { it.uri == uri }) {
          TextDocumentContentResult(text)
        }
        serverSession.expectNotification(serverSession.DID_CHANGE) {
          it.textDocument.uri == uri && it.textDocument.version == version &&
          it.contentChanges.singleOrNull()?.let { change -> change.range == null && change.text == text } == true
        }
        serverSession.sendRequest(serverSession.TEXT_DOCUMENT_CONTENT_REFRESH) { TextDocumentContentRefreshParams(uri) }
        replaced.await()
        serverSession.awaitExpected()
        assertEquals(text, readAction { document.text })
      }
    }
    finally {
      Disposer.dispose(disposable)
    }

    assertEquals("newer text", VfsUtilCore.loadText(file!!))
    assertFalse(file.isWritable, "The content file must stay read-only after a refresh")

    // a refresh for a URI without a content file completes without an error
    serverSession.sendRequest(serverSession.TEXT_DOCUMENT_CONTENT_REFRESH) { TextDocumentContentRefreshParams("jar:///nowhere.jar!/x/Y.class") }
  }

  private fun createJarEntry(): VirtualFile {
    val entryPath = "com/foo/Bar.txt"
    val jarPath = tempDir.resolve("lib.jar")
    ZipOutputStream(Files.newOutputStream(jarPath)).use { zip ->
      zip.putNextEntry(ZipEntry(entryPath))
      zip.write("class content".toByteArray())
      zip.closeEntry()
    }
    // the jar is created behind the VFS's back: bring the local file in first
    val localJarPath = jarPath.toString().replace('\\', '/')
    assertNotNull(StandardFileSystems.local().refreshAndFindFileByPath(localJarPath), localJarPath)
    val jarVfsPath = "$localJarPath!/$entryPath"
    val jarEntry = JarFileSystem.getInstance().refreshAndFindFileByPath(jarVfsPath)
    assertNotNull(jarEntry, jarVfsPath)
    return jarEntry!!
  }

  private suspend fun restartClientAndWait(manager: LspClientManagerImpl, client: LspClientImpl) {
    val running = CompletableDeferred<Unit>()
    val disposable = Disposer.newDisposable("LspDynamicFilesTest")
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
    val disposable = Disposer.newDisposable("LspDynamicFilesTest")
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

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl.features.navigation

import com.intellij.lang.Language
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClientDescriptor
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.platform.lsp.impl.getServerId
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.io.URLUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentContentParams
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import java.net.URI
import java.net.URISyntaxException
import java.util.concurrent.ConcurrentHashMap

/**
 * The dynamic files that one [LspClientImpl] serves: navigation targets whose text comes from the LSP server,
 * for example a class inside a jar or a JDK class from a `jrt:` URI.
 * The server provides the text through the `workspace/textDocumentContent` request,
 * for the URI schemes it declares in the `workspace.textDocumentContent` capability.
 * The text is served as a read-only in-memory file, one instance per URI, so repeated navigation reuses the same editor tab.
 * A content file is reported open (`didOpen`) with its URI once; it stays open for the whole session,
 * so the server can analyze requests inside it. A server-requested [refresh][refreshContent] syncs the replaced text
 * as a versioned `didChange`, so the server never answers requests against stale text.
 *
 * The registry belongs to one [LspClientImpl], so it dies with the server session, and different servers never share an entry.
 * A content file that lost its session, for example after a server restart, is [adopted][adopt] by the replacement
 * of the producing client, so an existing editor tab keeps working.
 */
internal class LspDynamicFiles(private val lspClient: LspClientImpl) {
  private val contentFiles = ConcurrentHashMap<String, VirtualFile>()

  /** True when this client served the content of the given [file]. */
  fun contains(file: VirtualFile): Boolean =
    getDynamicFileUri(file)?.let { contentFiles[it] == file } == true

  /**
   * The navigation target for [targetUri]: an in-memory file with server-provided content when the server declares
   * the URI scheme, otherwise the file [LspClientDescriptor.findFileByUri] resolves.
   */
  @RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
  fun findTargetFile(targetUri: String): VirtualFile? =
    if (lspClient.providesTextDocumentContent(uriScheme(targetUri))) getOrRequestContent(targetUri)
    else lspClient.descriptor.findFileByUri(targetUri)

  @RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
  fun getOrRequestContent(uri: String): VirtualFile? {
    contentFiles[uri]?.let { return it }
    if (!lspClient.providesTextDocumentContent(uriScheme(uri))) return null

    val result = lspClient.sendRequestSync { it.workspaceService.textDocumentContent(TextDocumentContentParams(uri)) }
    val text = result?.text ?: return null

    val file = createFile(uri, text)
    contentFiles.putIfAbsent(uri, file)?.let { return it }
    sendDidOpen(uri, file)
    return file
  }

  /**
   * Handles a server `workspace/textDocumentContent/refresh` for [uri]: requests the text again and
   * replaces the content of the existing file, so an open editor shows the fresh text.
   * A URI without a content file needs no work, because [getOrRequestContent] fetches the current text.
   */
  fun refreshContent(uri: String) {
    val file = contentFiles[uri] as? LightVirtualFile ?: return
    LspClientManagerImpl.getInstanceImpl(lspClient.project).cs.launch {
      val result = lspClient.sendRequest { it.workspaceService.textDocumentContent(TextDocumentContentParams(uri)) }
      val text = result?.text ?: return@launch
      withContext(Dispatchers.EDT) {
        // the content file is read-only for the user; lift the flag only for the content replacement
        file.isWritable = true
        try {
          file.setContent(null, text, false)
        }
        finally {
          file.isWritable = false
        }
        // reloads the read-only document from the replaced file content, like an external file change
        FileDocumentManager.getInstance().reloadFiles(file)
        // the server holds the document open with the text of the didOpen: sync the replaced text as a full change
        val version = lspClient.documentSyncManager.nextDocumentVersion(file)
        val identifier = VersionedTextDocumentIdentifier(uri, version)
        val params = DidChangeTextDocumentParams(identifier, listOf(TextDocumentContentChangeEvent(text)))
        lspClient.sendNotification { it.textDocumentService.didChange(params) }
      }
    }
  }

  /**
   * Adopts a content [file] that an earlier session of the same server produced, for example, before a restart.
   * Registers the file and reports it open again, so requests in an existing editor tab keep working.
   * Only the replacement of the producing client adopts: the file carries the producing server identity.
   * Returns `false` when another server produced the file, when this server provides no content for the URI scheme,
   * or when this session already serves another instance of [uri].
   */
  fun adopt(file: VirtualFile, uri: String): Boolean {
    contentFiles[uri]?.let { return it == file }
    if (file !is LightVirtualFile) return false
    if (file.getUserData(CONTENT_BY_SERVER_ID) != lspClient.getServerId()) return false
    if (!lspClient.providesTextDocumentContent(uriScheme(uri))) return false
    contentFiles.putIfAbsent(uri, file)?.let { return it == file }
    sendDidOpen(uri, file)
    return true
  }

  private fun sendDidOpen(uri: String, file: LightVirtualFile) {
    val version = lspClient.documentSyncManager.currentDocumentVersion(file)
    val textDocument = TextDocumentItem(uri, lspClient.descriptor.getLanguageId(file), version, file.content.toString())
    lspClient.sendNotification { it.textDocumentService.didOpen(DidOpenTextDocumentParams(textDocument)) }
  }

  private fun createFile(uri: String, text: String): LightVirtualFile {
    val name = URLUtil.unescapePercentSequences(uri.substringAfterLast('/'))
    val file = LightVirtualFile(name, fileTypeFor(name, text), text)
    file.isWritable = false
    file.putUserData(LspClientDescriptor.DYNAMIC_FILE_URI, uri)
    file.putUserData(CONTENT_BY_SERVER_ID, lspClient.getServerId())
    return file
  }

  /** The `workspace/textDocumentContent` result carries no language, so the file type comes from the client side. */
  private fun fileTypeFor(name: String, text: String): FileType {
    FileTypeRegistry.getInstance().getFileTypeByFileName(name)
      .takeUnless { it.isBinary || it is UnknownFileType }
      ?.let { return it }
    // the descriptor can map a name the platform has no text type for, e.g. `Bar.class`, to the decompiled language
    val languageId = lspClient.descriptor.getLanguageId(LightVirtualFile(name, PlainTextFileType.INSTANCE, text))
    val language = Language.getRegisteredLanguages().firstOrNull { it.id.equals(languageId, ignoreCase = true) }
    return language?.associatedFileType ?: PlainTextFileType.INSTANCE
  }

  companion object {
    /** The URI the in-memory content file was produced from, or `null` for a regular file. */
    fun getDynamicFileUri(file: VirtualFile): String? = file.getUserData(LspClientDescriptor.DYNAMIC_FILE_URI)

    internal val CONTENT_BY_SERVER_ID = Key.create<String>("lsp.content.by.server.id")
  }
}

private fun uriScheme(uri: String): String? = try {
  URI(uri).scheme
}
catch (_: URISyntaxException) {
  null
}

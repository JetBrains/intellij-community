// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl.features.navigation

import com.google.gson.JsonObject
import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.getServerId
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.io.URLUtil
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.TextDocumentItem
import java.net.URI
import java.net.URISyntaxException
import java.util.concurrent.ConcurrentHashMap

/**
 * The library files that one [LspClientImpl] serves: navigation targets outside the local source tree.
 * [findTargetFile] resolves a target URI from a server response, and remembers a target inside an archive,
 * so later requests about the file go back to the client that pointed at it.
 *
 * A location without a source text on the local disk (a class inside a jar without an attached sources jar,
 * or a JDK class from a `jrt:` URI) is served as a read-only in-memory file, one instance per URI,
 * so repeated navigation reuses the same editor tab. The LSP server provides the text via the `decompile` command
 * (advertised in `executeCommandProvider.commands`, the way the JetBrains language servers do).
 * A decompiled file is reported open (`didOpen`) with its library URI once; it stays open for the whole session,
 * so the server can analyze requests inside it.
 *
 * The registry belongs to one [LspClientImpl], so it dies with the server session, and different servers never share an entry.
 * A decompiled file that lost its session, for example after a server restart, is [adopted][adopt] by the replacement
 * of the producing client, so an existing editor tab keeps working.
 */
internal class LspLibraryFiles(private val lspClient: LspClientImpl) {
  private val decompiledFiles = ConcurrentHashMap<String, VirtualFile>()
  private val navigatedArchiveFiles = ConcurrentHashMap.newKeySet<VirtualFile>()

  /** True when this client produced the given [file]: decompiled it, or navigated to it inside an archive. */
  fun contains(file: VirtualFile): Boolean =
    navigatedArchiveFiles.contains(file) ||
    getDecompiledFileUri(file)?.let { decompiledFiles[it] == file } == true

  /** The navigation target for [targetUri]: a VFS file with a source text, or a decompiled in-memory file. */
  @RequiresBackgroundThread
  fun findTargetFile(targetUri: String): VirtualFile? {
    if (isDecompileOnlyUri(targetUri)) return getOrDecompile(targetUri)
    val file = lspClient.descriptor.findFileByUri(targetUri)
    if (file != null && !file.fileType.isBinary) {
      if (file.fileSystem is ArchiveFileSystem) navigatedArchiveFiles.add(file)
      return file
    }
    return getOrDecompile(targetUri) ?: file
  }

  @RequiresBackgroundThread
  fun getOrDecompile(uri: String): VirtualFile? {
    decompiledFiles[uri]?.let { return it }

    val scheme = try {
      URI(uri).scheme
    }
    catch (_: URISyntaxException) {
      null
    }
    if (scheme != URLUtil.JAR_PROTOCOL && scheme != JRT_PROTOCOL) return null
    if (!lspClient.supportsCommand(DECOMPILE_COMMAND)) return null

    val result = lspClient.sendRequestSync { it.workspaceService.executeCommand(ExecuteCommandParams(DECOMPILE_COMMAND, listOf(uri))) }
    val code = result.readStringField(CODE_FIELD) ?: return null
    val languageId = result.readStringField(LANGUAGE_FIELD)

    val file = createFile(uri, code, languageId)
    decompiledFiles.putIfAbsent(uri, file)?.let { return it }
    sendDidOpen(uri, file)
    return file
  }

  /**
   * Adopts a decompiled [file] that an earlier session of the same server produced, for example, before a restart.
   * Registers the file and reports it open again, so requests in an existing editor tab keep working.
   * Only the replacement of the producing client adopts: the file carries the producing server identity.
   * Returns `false` when another server produced the file, when this client cannot decompile,
   * or when this session already serves another instance of [uri].
   */
  fun adopt(file: VirtualFile, uri: String): Boolean {
    decompiledFiles[uri]?.let { return it == file }
    if (file !is LightVirtualFile) return false
    if (file.getUserData(DECOMPILED_BY_SERVER_ID) != lspClient.getServerId()) return false
    if (!lspClient.supportsCommand(DECOMPILE_COMMAND)) return false
    decompiledFiles.putIfAbsent(uri, file)?.let { return it == file }
    sendDidOpen(uri, file)
    return true
  }

  private fun sendDidOpen(uri: String, file: LightVirtualFile) {
    val languageId = file.getUserData(DECOMPILED_LANGUAGE_ID) ?: lspClient.descriptor.getLanguageId(file)
    val textDocument = TextDocumentItem(uri, languageId, 0, file.content.toString())
    lspClient.sendNotification { it.textDocumentService.didOpen(DidOpenTextDocumentParams(textDocument)) }
  }

  private fun createFile(uri: String, code: String, languageId: String?): LightVirtualFile {
    val language = languageId?.let { id -> Language.getRegisteredLanguages().firstOrNull { it.id.equals(id, ignoreCase = true) } }
    val name = URLUtil.unescapePercentSequences(uri.substringAfterLast('/'))
    val file = LightVirtualFile(name, language?.associatedFileType ?: PlainTextFileType.INSTANCE, code)
    file.isWritable = false
    file.putUserData(DECOMPILED_FROM_URI, uri)
    file.putUserData(DECOMPILED_LANGUAGE_ID, languageId ?: lspClient.descriptor.getLanguageId(file))
    file.putUserData(DECOMPILED_BY_SERVER_ID, lspClient.getServerId())
    return file
  }

  companion object {
    /** The library URI the decompiled in-memory file was produced from, or `null` for a regular file. */
    fun getDecompiledFileUri(file: VirtualFile): String? = file.getUserData(DECOMPILED_FROM_URI)

    /** True for a URI that no [VirtualFile] lookup can serve, only the `decompile` command: a `jrt:` JDK location. */
    private fun isDecompileOnlyUri(uri: String): Boolean = uri.startsWith("$JRT_PROTOCOL:")

    private val DECOMPILED_FROM_URI = Key.create<String>("lsp.decompiled.from.uri")
    private val DECOMPILED_LANGUAGE_ID = Key.create<String>("lsp.decompiled.language.id")
    internal val DECOMPILED_BY_SERVER_ID = Key.create<String>("lsp.decompiled.by.server.id")

    private const val DECOMPILE_COMMAND = "decompile"
    private const val JRT_PROTOCOL = "jrt"
    private const val CODE_FIELD = "code"
    private const val LANGUAGE_FIELD = "language"
  }
}

/**
 * The URI for a request about [file]: the library URI a decompiled in-memory file was produced from,
 * or [getFileUri][com.intellij.platform.lsp.api.LspClientDescriptor.getFileUri] for a regular file.
 */
internal fun LspClient.getFileUriForRequests(file: VirtualFile): String =
  LspLibraryFiles.getDecompiledFileUri(file) ?: descriptor.getFileUri(file)

/**
 * The `workspace/executeCommand` result is untyped in lsp4j, so Gson deserializes an object response into a [Map].
 * A server that keeps the raw json sends a [JsonObject] instead, hence both shapes.
 */
private fun Any?.readStringField(name: String): String? = when (this) {
  is Map<*, *> -> get(name) as? String
  is JsonObject -> get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
  else -> null
}

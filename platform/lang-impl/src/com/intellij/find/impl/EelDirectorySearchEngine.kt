// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.find.DirectorySearchEngine
import com.intellij.find.FindModel
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.limits.FileSizeLimit
import com.intellij.platform.eel.fs.EelSearchEvent
import com.intellij.platform.eel.fs.EelSearchEvent.Skipped.Reason
import com.intellij.platform.eel.fs.EelSearchOptions
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.LocalEelDescriptor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.util.function.Consumer

/**
 * Streams candidate files from a remote directory through EEL.
 * The caller applies the scope and file filters, then searches the candidates for occurrences.
 * If the remote search fails, this engine supplies the directory's children for further expansion.
 */
@ApiStatus.Internal
class EelDirectorySearchEngine @VisibleForTesting constructor(private val edges: EelSearchEdges) : DirectorySearchEngine {
  constructor() : this(EelSearchEdges.production())

  override fun canSearch(findModel: FindModel): Boolean {
    if (!RegistryManager.getInstance().`is`("find.in.files.eel.remote.search")) return false
    if (findModel.isRegularExpressions) return false
    val query = findModel.stringToFind
    if (query.isEmpty() || query.any { it == '\n' || it == '\r' || it.code >= 128 }) return false
    return findModel.fileFilter?.none { it in "![]{}\\" } != false
  }

  override fun getWeight(directory: VirtualFile): Int {
    if (!directory.isDirectory) return -1
    val path = directory.fileSystem.getNioPath(directory) ?: return -1
    return if (edges.descriptorOf(path) === LocalEelDescriptor) -1 else 1
  }

  override fun searchDirectory(directory: VirtualFile, findModel: FindModel, consumer: Consumer<in Collection<VirtualFile>>) {
    ProgressManager.checkCanceled()
    val completed = runBlockingCancellable {
      try {
        val nioPath = directory.fileSystem.getNioPath(directory) ?: throw IOException("No NIO path for $directory")
        val eelPath = edges.eelPathOf(nioPath)
        val searchApi = edges.searchApiOf(eelPath.descriptor)
        if (searchApi == null) return@runBlockingCancellable false
        val masks = findModel.fileFilter?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        searchApi.search(EelSearchOptions(
          roots = listOf(eelPath),
          content = EelSearchOptions.ContentQuery(query = findModel.stringToFind, caseSensitive = findModel.isCaseSensitive),
          includeNameGlobs = masks,
          followSymlinks = true,
          maxFileSize = FileSizeLimit.getDefaultContentLoadLimit().toLong(),
        )).collect { event ->
          val path = when (event) {
            is EelSearchEvent.Hit -> event.path
            is EelSearchEvent.Skipped -> {
              if (event.isDirectory) {
                throw IOException("Search under $eelPath could not enumerate ${event.path}")
              }
              else if (event.reason == Reason.BINARY || event.reason == Reason.TOO_LARGE) {
                null // skip too large and binary files
              }
              else {
                // assume usages and let the clients search themselves
                event.path
              }
            }
            EelSearchEvent.Truncated -> throw IOException("Search under $eelPath was truncated")
          }

          if (path != null) {
            val file = resolveFileIgnoreVanishedOrThrow(path)
            if (file != null) {
              consumer.accept(listOf(file))
            }
          }
        }
        true
      }
      catch (e: Exception) {
        rethrowControlFlowException(e)
        LOG.warn("Remote search under ${directory.path} failed; use directory expansion", e)
        false
      }
    }

    if (!completed) {
      consumer.accept(directory.children.asList())
    }
  }

  private fun resolveFileIgnoreVanishedOrThrow(path: EelPath): VirtualFile? {
    val nioPath = edges.nioPathOf(path)
    return VirtualFileManager.getInstance().findFileByNioPath(nioPath)

    // findFileByNioPath may return null also in these cases that should have been handled,
    // but did not because this code is invoked under RA:
    // 1. dirty VFS directory: we should use refreshAndFindFileByNioPath
    // 2. concurrent modification: file has been deleted immediately after it was discovered.
    //     Files.readAttributes(nioPath, BasicFileAttributes::class.java) throwing NoSuchFileException confirms this situation which
    //     should not be considered a failure.
    // All the other null-s should be considered as a failure:
    // throw IOException("Cannot resolve $path in VFS")
  }

  companion object {
    private val LOG = logger<EelDirectorySearchEngine>()
  }
}

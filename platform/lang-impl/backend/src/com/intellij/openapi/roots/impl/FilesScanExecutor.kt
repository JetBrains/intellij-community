// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.impl.VirtualFileEnumeration
import com.intellij.util.Processor
import com.intellij.util.TimeoutUtil
import com.intellij.util.containers.ConcurrentBitSet
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexEx
import com.intellij.util.indexing.IdFilter
import com.intellij.util.indexing.UnindexedFilesUpdater
import com.intellij.util.indexing.roots.IndexableFilesIterator
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin
import com.intellij.util.indexing.roots.kind.LibraryOrigin
import com.intellij.util.indexing.roots.kind.SdkOrigin
import com.intellij.util.indexing.roots.kind.SyntheticLibraryOrigin
import com.intellij.util.indexing.roots.origin.ExternalEntityOrigin
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger

@Internal
object FilesScanExecutor {
  private val LOG = Logger.getInstance(FilesScanExecutor::class.java)

  private fun isLibOrigin(origin: IndexableSetOrigin): Boolean {
    return origin is LibraryOrigin ||
           origin is SyntheticLibraryOrigin ||
           origin is SdkOrigin ||
           origin is ExternalEntityOrigin
  }

  @JvmStatic
  fun processFilesInScope(includingBinary: Boolean,
                          scope: GlobalSearchScope,
                          idFilter: IdFilter?,
                          processor: Processor<in VirtualFile?>): Boolean {
    ApplicationManager.getApplication().assertReadAccessAllowed()
    val project = scope.project ?: return true
    val fileIndex = ProjectRootManager.getInstance(project).fileIndex
    val searchInLibs = scope.isSearchInLibraries
    val deque = ConcurrentLinkedDeque<Any>()
    if (scope is VirtualFileEnumeration) {
      ContainerUtil.addAll<VirtualFile>(deque, FileBasedIndexEx.toFileIterable((scope as VirtualFileEnumeration).asArray()))
    }
    else {
      deque.addAll((FileBasedIndex.getInstance() as FileBasedIndexEx).getIndexableFilesProviders(project))
    }
    val skippedCount = AtomicInteger()
    val processedCount = AtomicInteger()
    val visitedFiles = ConcurrentBitSet.create(deque.size)
    val fileFilter = VirtualFileFilter { file: VirtualFile ->
      val fileId = FileBasedIndex.getFileId(file)
      if (visitedFiles.set(fileId)) return@VirtualFileFilter false
      val result = (idFilter == null || idFilter.containsFileId(fileId)) &&
                   !fileIndex.isExcluded(file) &&
                   scope.contains(file) &&
                   (includingBinary || file.isDirectory || !file.fileType.isBinary)
      if (!result) skippedCount.incrementAndGet()
      result
    }

    val consumer = l@ { obj: Any ->
      ProgressManager.checkCanceled()
      when (obj) {
        is IndexableFilesIterator -> {
          val origin = obj.origin
          if (!searchInLibs && isLibOrigin(origin)) return@l true
          obj.iterateFiles(project, { file: VirtualFile ->
            if (file.isDirectory) return@iterateFiles true
            deque.add(file)
            true
          }, fileFilter)
        }
        is VirtualFile -> {
          processedCount.incrementAndGet()
          if (!obj.isValid) {
            return@l true
          }
          return@l processor.process(obj)
        }
        else -> {
          throw AssertionError("unknown item: $obj")
        }
      }
      true
    }
    val start = System.nanoTime()
    val result = UnindexedFilesUpdater.processOnAllThreadsInReadActionNoRetries(deque, consumer)
    if (LOG.isDebugEnabled) {
      LOG.debug("${processedCount.get()} files processed (${skippedCount.get()} skipped) in ${TimeoutUtil.getDurationMillis(start)} ms")
    }
    return result
  }
}

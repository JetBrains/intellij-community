// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.projectFilter

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectCacheFileName
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.DataOutputStream
import com.intellij.util.io.createDirectories
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.div
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

private val filtersDir: Path
  get() = PathManager.getIndexRoot() / "index-file-filters"

private const val version = 1

/**
 * We don't have to delete these files explicitly
 * They won't be used anyway because [com.intellij.util.indexing.dependencies.AppIndexingDependenciesService.getCurrent]
 * will be changed and [com.intellij.util.indexing.isIndexableFilesFilterUpToDate] will return false.
 *
 * But it's better to do it explicitly.
 */
fun deletePersistentIndexableFilesFilters() {
  FileUtil.deleteWithRenaming(filtersDir)
}

internal class PersistentProjectIndexableFilesFilterFactory : ProjectIndexableFilesFilterFactory() {
  override fun create(project: Project): ProjectIndexableFilesFilter {
    try {
      val file = filtersDir.resolve(project.getProjectCacheFileName())
      val filter = DataInputStream(file.inputStream().buffered()).use {
        it.readInt() // version
        PersistentProjectIndexableFilesFilter(project, true, ConcurrentFileIds.readFrom(it))
      }
      file.deleteIfExists()
      return filter
    }
    catch (ignored: NoSuchFileException) {
    }
    catch (ignored: EOFException) {
    }
    catch (e: IOException) {
      thisLogger().error(e)
    }
    return PersistentProjectIndexableFilesFilter(project, false, ConcurrentFileIds())
  }
}

/**
 * Note about Invalidate Caches:
 * This filter doesn't require explicit caches invalidation because during invalidation AppIndexingDependenciesService
 * advances token which then causes filter to be rebuilt during next scanning
 * (see [com.intellij.util.indexing.UnindexedFilesScanner.isIndexableFilesFilterUpToDate])
 */
internal class PersistentProjectIndexableFilesFilter(project: Project, override val wasDataLoadedFromDisk: Boolean, fileIds: ConcurrentFileIds)
  : IncrementalProjectIndexableFilesFilter(project, fileIds) {

  override fun onProjectClosing(project: Project) {
    try {
      val file = filtersDir.resolve(project.getProjectCacheFileName())
      if (fileIds.empty) {
        file.deleteIfExists()
        return
      }
      file.parent.createDirectories()
      DataOutputStream(file.outputStream().buffered()).use {
        it.writeInt(version)
        fileIds.writeTo(it)
      }
    }
    catch (e: IOException) {
      thisLogger().error(e)
    }
  }
}
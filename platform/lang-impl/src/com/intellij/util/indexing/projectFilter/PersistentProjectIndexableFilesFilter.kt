// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.projectFilter

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectCacheFileName
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.DataOutputStream
import com.intellij.util.io.createDirectories
import org.jetbrains.annotations.ApiStatus.Internal
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

private const val version = 2

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

private val LOG = Logger.getInstance(PersistentProjectIndexableFilesFilterFactory::class.java)

internal class PersistentProjectIndexableFilesFilterFactory : ProjectIndexableFilesFilterFactory() {
  override fun create(project: Project, currentVfsCreationTimestamp: Long): ProjectIndexableFilesFilter {
    val file = filtersDir.resolve(project.getProjectCacheFileName())
    return readIndexableFilesFilter(file, currentVfsCreationTimestamp)
  }
}

@Internal
fun readIndexableFilesFilter(file: Path, currentVfsCreationTimestamp: Long): ProjectIndexableFilesFilter {
  try {
    val filter = DataInputStream(file.inputStream().buffered()).use {
      val version = it.readInt() // version
      val vfsCreationTimestamp = when (version) {
        1 -> -1 // vfsCreationTimestamp was not saved in version 1
        2 -> it.readLong()
        else -> {
          LOG.error("Unknown PersistentProjectIndexableFilesFilter version $version")
          -1
        }
      }
      if (vfsCreationTimestamp == currentVfsCreationTimestamp) {
        PersistentProjectIndexableFilesFilter(true, ConcurrentFileIds.readFrom(it))
      }
      else null
    }
    file.deleteIfExists()
    if (filter != null) {
      return filter
    }
  }
  catch (_: NoSuchFileException) {
  }
  catch (_: EOFException) {
  }
  catch (e: IOException) {
    LOG.error(e)
  }
  return PersistentProjectIndexableFilesFilter(false, ConcurrentFileIds())
}

/**
 * Note about Invalidate Caches:
 * This filter doesn't require explicit caches invalidation because during invalidation AppIndexingDependenciesService
 * advances token which then causes filter to be rebuilt during next scanning
 * (see [com.intellij.util.indexing.UnindexedFilesScanner.isIndexableFilesFilterUpToDate])
 */
internal class PersistentProjectIndexableFilesFilter(override val wasDataLoadedFromDisk: Boolean, fileIds: ConcurrentFileIds)
  : IncrementalProjectIndexableFilesFilter(fileIds) {

  override fun onProjectClosing(project: Project, vfsCreationStamp: Long) {
    val file = filtersDir.resolve(project.getProjectCacheFileName())
    try {
      if (fileIds.empty) {
        file.deleteIfExists()
        return
      }
      file.parent.createDirectories()
      DataOutputStream(file.outputStream().buffered()).use {
        it.writeInt(version)
        it.writeLong(vfsCreationStamp)
        fileIds.writeTo(it)
      }
    }
    catch (e: IOException) {
      LOG.error(e)
    }
  }
}
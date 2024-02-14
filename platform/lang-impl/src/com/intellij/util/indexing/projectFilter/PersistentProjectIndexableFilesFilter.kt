// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.projectFilter

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectCacheFileName
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

internal class PersistentProjectIndexableFilesFilterFactory : ProjectIndexableFilesFilterFactory {
  override fun create(project: Project): ProjectIndexableFilesFilter {
    try {
      val file = filtersDir.resolve(project.getProjectCacheFileName())
      val filter = DataInputStream(file.inputStream().buffered()).use {
        it.readInt() // version
        PersistentProjectIndexableFilesFilter(true, ConcurrentFileIds.readFrom(it))
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
    return PersistentProjectIndexableFilesFilter(false, ConcurrentFileIds())
  }
}

/**
 * Note about Invalidate Caches:
 * This filter doesn't require explicit caches invalidation because during invalidation AppIndexingDependenciesService
 * advances token which then causes filter to be rebuilt during next scanning
 * (see [com.intellij.util.indexing.UnindexedFilesScanner.isIndexableFilesFilterUpToDate])
 */
internal class PersistentProjectIndexableFilesFilter(override val wasDataLoadedFromDisk: Boolean, fileIds: ConcurrentFileIds) : IncrementalProjectIndexableFilesFilter(fileIds) {

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
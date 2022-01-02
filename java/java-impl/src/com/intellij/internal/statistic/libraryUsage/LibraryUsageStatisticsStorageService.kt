// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.libraryUsage

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.*
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSet
import com.intellij.util.xmlb.annotations.XMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

@Service(Service.Level.PROJECT)
@State(name = "LibraryUsageStatistics", storages = [Storage(StoragePathMacros.CACHE_FILE)])
class LibraryUsageStatisticsStorageService : PersistentStateComponent<LibraryUsageStatisticsStorageService.LibraryUsageStatisticsState>, Disposable {
  private val lock = ReentrantReadWriteLock()
  private var statistics = Object2IntOpenHashMap<LibraryUsage>()
  private var visitedFiles: VirtualFileSet = VfsUtilCore.createCompactVirtualFileSet()

  override fun getState(): LibraryUsageStatisticsState {
    return lock.read {
      val result = LibraryUsageStatisticsState()
      result.statistics.putAll(statistics)
      result
    }
  }

  override fun loadState(state: LibraryUsageStatisticsState) {
    lock.write {
      statistics = Object2IntOpenHashMap(state.statistics)
      visitedFiles = VfsUtilCore.createCompactVirtualFileSet()
    }
  }

  fun getStatisticsAndResetState(): Map<LibraryUsage, Int> {
    lock.write {
      val old = statistics
      statistics = Object2IntOpenHashMap()
      visitedFiles = VfsUtilCore.createCompactVirtualFileSet()
      return old
    }
  }

  fun isVisited(vFile: VirtualFile): Boolean = lock.read { vFile in visitedFiles }

  fun visit(vFile: VirtualFile): Boolean = lock.write { visitedFiles.add(vFile) }

  fun increaseUsage(vFile: VirtualFile, library: LibraryUsage) {
    lock.write {
      unsafeIncreaseUsage(library)
      visitedFiles.add(vFile)
    }
  }

  fun increaseUsages(vFile: VirtualFile, libraries: Collection<LibraryUsage>): Unit = lock.write {
    libraries.forEach(::unsafeIncreaseUsage)
    visitedFiles.add(vFile)
  }

  private fun unsafeIncreaseUsage(library: LibraryUsage) {
    statistics.addTo(library, 1)
  }

  class LibraryUsageStatisticsState {
    @XMap
    @JvmField
    val statistics = HashMap<LibraryUsage, Int>()
  }

  override fun dispose() = Unit

  companion object {
    fun getInstance(project: Project): LibraryUsageStatisticsStorageService = project.service()
  }
}

data class LibraryUsage(
  var name: String? = null,
  var version: String? = null,
  var fileTypeName: String? = null,
) {
  constructor(name: String, version: String, fileType: FileType) : this(name = name, version = version, fileTypeName = fileType.name)

  override fun toString(): String = "$name-$version for $fileTypeName"
}


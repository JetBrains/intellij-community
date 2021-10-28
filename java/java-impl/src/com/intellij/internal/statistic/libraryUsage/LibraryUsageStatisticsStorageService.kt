// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.libraryUsage

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.*
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSet
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

@Service(Service.Level.PROJECT)
@State(name = "LibraryUsageStatistics", storages = [Storage(StoragePathMacros.CACHE_FILE, roamingType = RoamingType.DISABLED)])
class LibraryUsageStatisticsStorageService : PersistentStateComponent<LibraryUsageStatisticsStorageService.LibraryUsageStatisticsState>, Disposable {
  private val lock = ReentrantReadWriteLock()
  private var statistics: MutableMap<LibraryUsage, Int> = mutableMapOf()
  private var visitedFiles: VirtualFileSet = VfsUtilCore.createCompactVirtualFileSet()

  override fun getState(): LibraryUsageStatisticsState = lock.read { LibraryUsageStatisticsState(statistics) }

  override fun loadState(state: LibraryUsageStatisticsState): Unit = lock.write {
    statistics = state.statistics
    visitedFiles = VfsUtilCore.createCompactVirtualFileSet()
  }

  fun getStatisticsAndResetState(): Map<LibraryUsage, Int> = lock.write {
    statistics.also {
      statistics = mutableMapOf()
      visitedFiles = VfsUtilCore.createCompactVirtualFileSet()
    }
  }

  fun isVisited(vFile: VirtualFile): Boolean = lock.read { vFile in visitedFiles }
  fun visit(vFile: VirtualFile): Boolean = lock.write { visitedFiles.add(vFile) }
  fun increaseUsage(vFile: VirtualFile, library: LibraryUsage): Unit = lock.write {
    unsafeIncreaseUsage(library)
    visitedFiles += vFile
  }

  fun increaseUsages(vFile: VirtualFile, libraries: Collection<LibraryUsage>): Unit = lock.write {
    libraries.forEach(::unsafeIncreaseUsage)
    visitedFiles += vFile
  }

  private fun unsafeIncreaseUsage(library: LibraryUsage) {
    statistics.compute(library) { _, old -> old?.inc() ?: 1 }
  }

  class LibraryUsageStatisticsState(
    var statistics: MutableMap<LibraryUsage, Int> = mutableMapOf()
  )

  override fun dispose() = Unit

  companion object {
    operator fun get(project: Project): LibraryUsageStatisticsStorageService = project.service()
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


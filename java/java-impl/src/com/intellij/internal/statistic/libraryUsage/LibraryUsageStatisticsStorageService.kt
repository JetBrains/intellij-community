// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.libraryUsage

import com.intellij.openapi.components.*
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.XMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

@Service(Service.Level.PROJECT)
@State(
  name = "LibraryUsageStatistics",
  storages = [Storage(StoragePathMacros.CACHE_FILE)],
  reportStatistic = false,
  reloadable = false,
)
@ApiStatus.Internal
public class LibraryUsageStatisticsStorageService : PersistentStateComponent<LibraryUsageStatisticsStorageService.LibraryUsageStatisticsState> {
  private val lock = ReentrantReadWriteLock()
  private var statistics = Object2IntOpenHashMap<LibraryUsage>()

  override fun getState(): LibraryUsageStatisticsState = lock.read {
    val result = LibraryUsageStatisticsState()
    result.statistics.putAll(statistics)
    result
  }

  override fun loadState(state: LibraryUsageStatisticsState): Unit = lock.write {
    statistics = Object2IntOpenHashMap(state.statistics)
  }

  public fun getStatisticsAndResetState(): Map<LibraryUsage, Int> = lock.write {
    val old = statistics
    statistics = Object2IntOpenHashMap()
    old
  }

  public fun increaseUsages(libraries: Collection<LibraryUsage>): Unit = lock.write {
    for (it in libraries) {
      statistics.addTo(it, 1)
    }
  }

  public class LibraryUsageStatisticsState {
    @XMap
    @JvmField
    public val statistics: HashMap<LibraryUsage, Int> = HashMap()
  }

  public companion object {
    public fun getInstance(project: Project): LibraryUsageStatisticsStorageService = project.service()
  }
}

public data class LibraryUsage(
  var name: String? = null,
  var version: String? = null,
  var fileTypeName: String? = null,
) {
  public constructor(name: String, version: String, fileType: FileType) : this(name = name, version = version, fileTypeName = fileType.name)

  override fun toString(): String = "$name-$version for $fileTypeName"
}

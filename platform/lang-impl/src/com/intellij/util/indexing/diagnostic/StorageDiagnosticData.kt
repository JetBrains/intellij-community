// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.intellij.diagnostic.telemetry.ReentrantReadWriteLockUsageMonitor
import com.intellij.diagnostic.telemetry.TraceManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.indexing.ID
import com.intellij.util.indexing.IndexInfrastructure
import com.intellij.util.io.DirectByteBufferAllocator
import com.intellij.util.io.StorageLockContext
import com.intellij.util.io.delete
import com.intellij.util.io.stats.FilePageCacheStatistics
import com.intellij.util.io.stats.PersistentEnumeratorStatistics
import com.intellij.util.io.stats.PersistentHashMapStatistics
import com.intellij.util.io.stats.StorageStatsRegistrar
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.io.path.absolute
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString
import kotlin.io.path.relativeTo

/**
 * Dumps general storage diagnostic information. Almost every open [com.intellij.util.io.PersistentMapImpl],
 * [com.intellij.util.io.PersistentEnumeratorBase] and [com.intellij.util.io.ResizeableMappedFile] contribute to diagnostic.
 * This means index storages, vfs storages and other plugin specific storages are included.
 */
object StorageDiagnosticData {
  private val MONITOR_STORAGE_LOCK = SystemProperties.getBooleanProperty("vfs.storage-lock.enable-diagnostic", true)

  private const val fileNamePrefix = "storage-diagnostic-"
  private const val onShutdownFileNameSuffix = "on-shutdown-"

  private const val maxFiles = 10
  private const val dumpPeriodInMinutes = 1L

  @JvmStatic
  fun dumpPeriodically() {
    setupReportingToOpenTelemetry()

    val executor = AppExecutorUtil.createBoundedScheduledExecutorService(
      "Storage Diagnostic Dumper",
      1,
    )

    executor.scheduleWithFixedDelay(Runnable {
      dump(onShutdown = false)
    }, dumpPeriodInMinutes, dumpPeriodInMinutes, TimeUnit.MINUTES)
  }

  @JvmStatic
  fun dumpOnShutdown() {
    dump(onShutdown = true)
  }

  @Synchronized
  private fun dump(onShutdown: Boolean) {
    val sessionStartTime = ApplicationManager.getApplication().startTime
    val sessionLocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(sessionStartTime), ZoneId.systemDefault())

    try {
      val stats = getStorageDataStatistics()
      val file = getDumpFile(sessionLocalDateTime, onShutdown)
      IndexDiagnosticDumperUtils.writeValue(file, stats)
    }
    catch (e: Exception) {
      thisLogger().error(e)
    }
    finally {
      deleteOutdatedDiagnostics(onShutdown)
    }
  }

  private fun getDumpFile(time: LocalDateTime, onShutdown: Boolean): Path = IndexDiagnosticDumperUtils.getDumpFilePath(
    fileNamePrefix,
    time,
    "json",
    IndexDiagnosticDumperUtils.indexingDiagnosticDir,
    suffix = if (onShutdown) onShutdownFileNameSuffix else ""
  )

  @VisibleForTesting
  fun getStorageDataStatistics(): StorageDataStats {
    val mapStats = StorageStatsRegistrar.dumpStatsForOpenMaps().toMutableMap()
    val enumeratorStats = StorageStatsRegistrar.dumpStatsForOpenEnumerators().toMutableMap()
    val vfsStorageStats = vfsStorageStatistics(mapStats, enumeratorStats)
    val indexStorageStats = indexStorageStatistics(mapStats, enumeratorStats)
    val otherStorageStats = otherGeneralStorageStatistics(mapStats, enumeratorStats)

    val pageCacheStats = StorageLockContext.getStatistics()

    return StorageDataStats(
      pageCacheStats,
      vfsStorageStats.nullize(),
      indexStorageStats,
      otherStorageStats.nullize()
    )
  }

  private fun deleteOutdatedDiagnostics(onShutdown: Boolean) {
    val suffix = if (onShutdown) onShutdownFileNameSuffix else ""
    val allDiagnosticFiles = IndexDiagnosticDumperUtils
      .indexingDiagnosticDir
      .listDirectoryEntries("$fileNamePrefix$suffix[0-9]*")
      .sortedBy { it.fileName }
    if (allDiagnosticFiles.size - maxFiles > 0) {
      val outdatedFiles = allDiagnosticFiles.take(allDiagnosticFiles.size - maxFiles)

      for (outdatedFile in outdatedFiles) {
        outdatedFile.delete()
      }
    }
  }

  private fun otherGeneralStorageStatistics(mapStats: Map<Path, PersistentHashMapStatistics>,
                                            enumeratorStats: Map<Path, PersistentEnumeratorStatistics>): StatsPerStorage {
    val macroManager = PathMacroManager.getInstance(ApplicationManager.getApplication())
    return StatsPerStorage(
      mapStats.mapKeys { macroManager.collapsePath(it.key.pathString) }.toSortedMap(),
      enumeratorStats.mapKeys { macroManager.collapsePath(it.key.pathString) }.toSortedMap()
    )
  }

  private fun indexStorageStatistics(mapStats: MutableMap<Path, PersistentHashMapStatistics>,
                                     enumeratorStats: MutableMap<Path, PersistentEnumeratorStatistics>): IndexStorageStats {

    val perIndexStats = sortedMapOf<String, StatsPerStorage>()
    for (id in ID.getRegisteredIds()) {
      val indexStats = listOf(IndexInfrastructure.getIndexRootDir(id), IndexInfrastructure.getPersistentIndexRootDir(id))
        .map {
          StatsPerStorage(
            filterStatsForStoragesUnderDir(mapStats, it),
            filterStatsForStoragesUnderDir(enumeratorStats, it)
          )
        }.fold(StatsPerStorage.EMPTY) { a, b -> a + b }
      if (!indexStats.isEmpty()) {
        perIndexStats.put(id.name, indexStats)
      }
    }

    val indexDir = PathManager.getIndexRoot()
    val otherIndexStorageStats = StatsPerStorage(
      filterStatsForStoragesUnderDir(mapStats, indexDir),
      filterStatsForStoragesUnderDir(enumeratorStats, indexDir)
    ).nullize()

    return IndexStorageStats(perIndexStats, otherIndexStorageStats)
  }

  private fun vfsStorageStatistics(mapStats: MutableMap<Path, PersistentHashMapStatistics>,
                                   enumeratorStats: MutableMap<Path, PersistentEnumeratorStatistics>)
    : StatsPerStorage {
    val cachesDir = Path.of(FSRecords.getCachesDir()).absolute()
    return StatsPerStorage(
      filterStatsForStoragesUnderDir(mapStats, cachesDir),
      filterStatsForStoragesUnderDir(enumeratorStats, cachesDir)
    )
  }

  private fun <Stats> filterStatsForStoragesUnderDir(mapStats: MutableMap<Path, Stats>, dir: Path): SortedMap<String, Stats> {
    check(dir.isAbsolute)
    val filtered = mapStats.filterKeys { it.fileSystem == dir.fileSystem && it.startsWith(dir) }

    for (key in filtered.keys) {
      mapStats.remove(key)
    }

    return filtered.mapKeys { it.key.relativeTo(dir).pathString }.toSortedMap()
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonIgnoreProperties(ignoreUnknown = true)
  data class IndexStorageStats(
    val indexStoragesStats: Map<String, StatsPerStorage>,
    val otherStoragesStats: StatsPerStorage?,
  )

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  @JsonIgnoreProperties(ignoreUnknown = true)
  data class StatsPerStorage(
    val statsPerPhm: SortedMap<String, PersistentHashMapStatistics>,
    val statsPerEnumerator: SortedMap<String, PersistentEnumeratorStatistics>,
  ) {
    companion object {
      val EMPTY = StatsPerStorage(sortedMapOf(), sortedMapOf())
    }

    operator fun plus(another: StatsPerStorage): StatsPerStorage {
      return StatsPerStorage(
        (statsPerPhm + another.statsPerPhm).toSortedMap(),
        (statsPerEnumerator + another.statsPerEnumerator).toSortedMap()
      )
    }

    @JsonIgnore
    fun isEmpty(): Boolean {
      return statsPerPhm.isEmpty() && statsPerEnumerator.isEmpty()
    }

    fun nullize(): StatsPerStorage? = if (isEmpty()) null else this
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonIgnoreProperties(ignoreUnknown = true)
  data class StorageDataStats(
    val pageCacheStats: FilePageCacheStatistics,
    val vfsStorageStats: StatsPerStorage?,
    val indexStorageStats: IndexStorageStats?,
    val otherStorageStats: StatsPerStorage?,
  )

  /* =========================== Monitoring via OpenTelemetry: ========================================== */

  //TODO RC: i'd think it is better to setup such monitoring in apt. component itself, because be
  //         observable is the responsibility of component, the same way as logging is. But:
  //         a) FilePageCache module hasn't dependency on the monitoring module now
  //         b) here we already have monitoring of FilePageCache, so better to keep old/new
  //            monitoring in one place for a while
  private fun setupReportingToOpenTelemetry() {
    val otelMeter = TraceManager.getMeter("storage")

    if (MONITOR_STORAGE_LOCK) {
      ReentrantReadWriteLockUsageMonitor(
        StorageLockContext.defaultContextLock(),
        "StorageLockContext",
        otelMeter
      )
    }

    val uncachedFileAccess = otelMeter.counterBuilder("FilePageCache.uncachedFileAccess").buildObserver()
    val maxRegisteredFiles = otelMeter.gaugeBuilder("FilePageCache.maxRegisteredFiles").ofLongs().buildObserver()

    val pageHits = otelMeter.counterBuilder("FilePageCache.pageHits").buildObserver()
    val pageFastCacheHit = otelMeter.counterBuilder("FilePageCache.pageFastCacheHits").buildObserver()
    val pageLoads = otelMeter.counterBuilder("FilePageCache.pageLoads").buildObserver()
    val pageLoadsAboveSizeThreshold = otelMeter.counterBuilder("FilePageCache.pageLoadsAboveSizeThreshold").buildObserver()
    val totalPageLoadsUs = otelMeter.counterBuilder("FilePageCache.totalPageLoadsUs")
      .setUnit("us").buildObserver()
    val totalPageDisposalsUs = otelMeter.counterBuilder("FilePageCache.totalPageDisposalsUs")
      .setUnit("us").buildObserver()

    val disposedBuffers = otelMeter.counterBuilder("FilePageCache.disposedBuffers").buildObserver()

    val totalCachedSizeInBytes = otelMeter.gaugeBuilder("FilePageCache.totalCachedSizeInBytes")
      .setUnit("bytes")
      .setDescription("Total size of all pages currently cached")
      .ofLongs().buildObserver()
    val maxCacheSizeInBytes = otelMeter.gaugeBuilder("FilePageCache.maxCacheSizeInBytes")
      .setUnit("bytes")
      .setDescription("Max size of all cached pages observed since application start")
      .ofLongs().buildObserver()
    val capacityInBytes = otelMeter.gaugeBuilder("FilePageCache.capacityInBytes")
      .setUnit("bytes")
      .setDescription("Cache capacity, configured on application startup")
      .ofLongs().buildObserver()

    val directBufferAllocatorHits = otelMeter.counterBuilder("DirectByteBufferAllocator.hits").buildObserver()
    val directBufferAllocatorMisses = otelMeter.counterBuilder("DirectByteBufferAllocator.misses").buildObserver()
    val directBufferAllocatorReclaimed = otelMeter.counterBuilder("DirectByteBufferAllocator.reclaimed").buildObserver()
    val directBufferAllocatorDisposed = otelMeter.counterBuilder("DirectByteBufferAllocator.disposed").buildObserver()
    val directBufferAllocatorTotalSizeCached = otelMeter.counterBuilder(
      "DirectByteBufferAllocator.totalSizeOfBuffersCachedInBytes").buildObserver()

    otelMeter.batchCallback(
      {
        try {
          val pageCacheStats = StorageLockContext.getStatistics()
          uncachedFileAccess.record(pageCacheStats.uncachedFileAccess.toLong())
          maxRegisteredFiles.record(pageCacheStats.maxRegisteredFiles.toLong())

          maxCacheSizeInBytes.record(pageCacheStats.maxCacheSizeInBytes)
          capacityInBytes.record(pageCacheStats.capacityInBytes)
          totalCachedSizeInBytes.record(pageCacheStats.totalCachedSizeInBytes)

          pageFastCacheHit.record(pageCacheStats.pageFastCacheHits.toLong())

          pageHits.record(pageCacheStats.pageHits.toLong())
          pageLoads.record(pageCacheStats.regularPageLoads.toLong())
          pageLoadsAboveSizeThreshold.record(pageCacheStats.pageLoadsAboveSizeThreshold.toLong())

          totalPageLoadsUs.record(pageCacheStats.totalPageLoadUs)
          totalPageDisposalsUs.record(pageCacheStats.totalPageDisposalUs)

          disposedBuffers.record(pageCacheStats.disposedBuffers.toLong())

          val bufferAllocatorStats = DirectByteBufferAllocator.ALLOCATOR.statistics
          directBufferAllocatorHits.record(bufferAllocatorStats.hits.toLong())
          directBufferAllocatorMisses.record(bufferAllocatorStats.misses.toLong())
          directBufferAllocatorReclaimed.record(bufferAllocatorStats.reclaimed.toLong())
          directBufferAllocatorDisposed.record(bufferAllocatorStats.disposed.toLong())
          directBufferAllocatorTotalSizeCached.record(bufferAllocatorStats.totalSizeOfBuffersCachedInBytes.toLong())
        }
        catch (_: AlreadyDisposedException) {

        }
      },
      uncachedFileAccess, maxRegisteredFiles, maxCacheSizeInBytes,
      pageHits, pageFastCacheHit, pageLoadsAboveSizeThreshold, pageLoads,
      totalPageLoadsUs, totalPageDisposalsUs,
      disposedBuffers, capacityInBytes,

      directBufferAllocatorHits, directBufferAllocatorMisses,
      directBufferAllocatorReclaimed, directBufferAllocatorDisposed,
      directBufferAllocatorTotalSizeCached
    )
  }
}

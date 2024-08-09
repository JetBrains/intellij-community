// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.util.indexing.diagnostic

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords
import com.intellij.openapi.vfs.newvfs.persistent.mapped.MappedStorageOTelMonitor
import com.intellij.platform.diagnostic.telemetry.Indexes
import com.intellij.platform.diagnostic.telemetry.Storage
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.impl.helpers.ReentrantReadWriteLockUsageMonitor
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.indexing.ID
import com.intellij.util.indexing.IndexInfrastructure
import com.intellij.util.indexing.contentQueue.dev.IndexWriter
import com.intellij.util.indexing.impl.MapIndexStorageCacheProvider
import com.intellij.util.io.*
import com.intellij.util.io.stats.FilePageCacheStatistics
import com.intellij.util.io.stats.PersistentEnumeratorStatistics
import com.intellij.util.io.stats.PersistentHashMapStatistics
import com.intellij.util.io.stats.StorageStatsRegistrar
import io.opentelemetry.api.metrics.Meter
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString
import kotlin.io.path.relativeTo
import kotlin.math.max
import kotlin.math.min

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

  @Volatile
  private var regularDumpHandle: Future<*>? = null

  @Volatile
  private var mmappedStoragesMonitoringHandle: MappedStorageOTelMonitor? = null

  @JvmStatic
  fun startPeriodicDumping() {
    setupReportingToOpenTelemetry()

    val executor = AppExecutorUtil.createBoundedScheduledExecutorService(
      "Storage Diagnostic Dumper",
      1,
    )

    regularDumpHandle = executor.scheduleWithFixedDelay(Runnable {
      dump(onShutdown = false)
    }, dumpPeriodInMinutes, dumpPeriodInMinutes, TimeUnit.MINUTES)
  }

  @JvmStatic
  fun dumpOnShutdown() {
    //Since we know it is a shutdown -> cancel regular stats dumping:
    val regularDumpHandleLocalCopy = regularDumpHandle
    if (regularDumpHandleLocalCopy != null) {
      regularDumpHandleLocalCopy.cancel(false)
      regularDumpHandle = null
    }

    val mmappedStoragesMonitoringHandleLocalCopy = mmappedStoragesMonitoringHandle
    if (mmappedStoragesMonitoringHandleLocalCopy != null) {
      mmappedStoragesMonitoringHandleLocalCopy.close()
      mmappedStoragesMonitoringHandle = null
    }

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
    catch (e: AlreadyDisposedException) {
      //e.g. IDEA-313757
      thisLogger().info("Can't collect storage statistics: ${e.message} -- probably, already a shutdown?")
    }
    catch (e: IOException) {
      thisLogger().warn(e)
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

  private fun otherGeneralStorageStatistics(
    mapStats: Map<Path, PersistentHashMapStatistics>,
    enumeratorStats: Map<Path, PersistentEnumeratorStatistics>,
  ): StatsPerStorage {
    val macroManager = PathMacroManager.getInstance(ApplicationManager.getApplication())
    return StatsPerStorage(
      mapStats.mapKeys { macroManager.collapsePath(it.key.pathString) }.toSortedMap(),
      enumeratorStats.mapKeys { macroManager.collapsePath(it.key.pathString) }.toSortedMap()
    )
  }

  private fun indexStorageStatistics(
    mapStats: MutableMap<Path, PersistentHashMapStatistics>,
    enumeratorStats: MutableMap<Path, PersistentEnumeratorStatistics>,
  ): IndexStorageStats {

    val perIndexStats = TreeMap<String, StatsPerStorage>()
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

  private fun vfsStorageStatistics(
    mapStats: MutableMap<Path, PersistentHashMapStatistics>,
    enumeratorStats: MutableMap<Path, PersistentEnumeratorStatistics>,
  )
    : StatsPerStorage {
    val cacheDir = FSRecords.getCacheDir().toAbsolutePath()
    return StatsPerStorage(filterStatsForStoragesUnderDir(mapStats, cacheDir), filterStatsForStoragesUnderDir(enumeratorStats, cacheDir))
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
      val EMPTY: StatsPerStorage = StatsPerStorage(sortedMapOf(), sortedMapOf())
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
  //         a) FilePageCache module (intellij.platform.util) has no dependency on the monitoring module now
  //         b) we already have monitoring of FilePageCache here, so better to keep old/new monitoring in one
  //            place for a while
  private fun setupReportingToOpenTelemetry() {
    val storageOtelMeter = TelemetryManager.getMeter(Storage)

    if (MONITOR_STORAGE_LOCK) {
      ReentrantReadWriteLockUsageMonitor(
        StorageLockContext.defaultContextLock(),
        "StorageLockContext",
        storageOtelMeter
      )
    }

    setupFilePageCacheReporting(storageOtelMeter)

    if (PageCacheUtils.LOCK_FREE_PAGE_CACHE_ENABLED) {
      setupFilePageCacheLockFreeReporting(storageOtelMeter)
    }

    storageOtelMeter.counterBuilder("FileChannelInterruptsRetryer.totalRetriedAttempts").buildWithCallback {
      it.record(FileChannelInterruptsRetryer.totalRetriedAttempts())
    }


    mmappedStoragesMonitoringHandle = MappedStorageOTelMonitor(storageOtelMeter)

    setupIndexingReporting(TelemetryManager.getMeter(Indexes))
  }

  private fun setupIndexingReporting(otelMeter: Meter) {
    //Indexes writers:
    val defaultParallelWriter = IndexWriter.defaultParallelWriter()
    (0..<defaultParallelWriter.workersCount).forEach { workerNo ->
      otelMeter.counterBuilder("Indexing.writer_$workerNo.totalTimeSpentWritingMs").buildWithCallback {
        it.record(defaultParallelWriter.totalTimeSpentWriting(MILLISECONDS, workerNo))
      }
    }

    //Indexing queue (indexers->writers):
    // To calculate unbiased stats we should sample the queue such that the sampling moments are _not_ correlated
    // with enqueue/dequeue moments -- which is why we can't collect the stats at enqueue/dequeue methods, and
    // must sample queue independently here:
    var writesQueuedMax: Long = 0
    var writesQueuedMin: Long = Long.MAX_VALUE
    var writesQueuedSum: Long = 0
    var writesQueuedMeasurements: Int = 0
    GlobalScope.launch {
      while (true) {
        val writesQueued = defaultParallelWriter.writesQueued().toLong()
        writesQueuedMax = max(writesQueuedMax, writesQueued)
        writesQueuedMin = min(writesQueuedMin, writesQueued)
        writesQueuedSum += writesQueued
        writesQueuedMeasurements++
        delay(542) // irregular number, uncorrelated with anything (supposedly)
      }
    }
    otelMeter.gaugeBuilder("Indexing.writesQueuedAvg").buildWithCallback {
      it.record(writesQueuedSum.toDouble() / writesQueuedMeasurements)
      writesQueuedSum = 0
      writesQueuedMeasurements = 0
    }
    otelMeter.gaugeBuilder("Indexing.writesQueuedMax").ofLongs().buildWithCallback {
      it.record(writesQueuedMax)
      writesQueuedMax = 0
    }
    otelMeter.gaugeBuilder("Indexing.writesQueuedMin").ofLongs().buildWithCallback {
      it.record(writesQueuedMin)
      writesQueuedMin = Long.MAX_VALUE
    }

    //indexers:
    otelMeter.counterBuilder("Indexing.totalTimeIndexersSleptMs").buildWithCallback {
      it.record(defaultParallelWriter.totalTimeIndexersSlept(MILLISECONDS))
    }

    //Indexes caches:
    val indexCacheProvider = MapIndexStorageCacheProvider.actualProvider
    otelMeter.counterBuilder("Indexing.cache.totalCacheAccesses").buildWithCallback {
      it.record(indexCacheProvider.totalReads())
    }
    otelMeter.counterBuilder("Indexing.cache.totalCacheMisses").buildWithCallback {
      it.record(indexCacheProvider.totalReadsUncached())
    }
    otelMeter.counterBuilder("Indexing.cache.totalCacheEvicted").buildWithCallback {
      it.record(indexCacheProvider.totalEvicted())
    }
  }

  private fun setupFilePageCacheLockFreeReporting(otelMeter: Meter) {
    val totalNativeBytesAllocated = otelMeter.counterBuilder("FilePageCacheLockFree.totalNativeBytesAllocated").buildObserver()
    val totalNativeBytesReclaimed = otelMeter.counterBuilder("FilePageCacheLockFree.totalNativeBytesReclaimed").buildObserver()
    val totalHeapBytesAllocated = otelMeter.counterBuilder("FilePageCacheLockFree.totalHeapBytesAllocated").buildObserver()
    val totalHeapBytesReclaimed = otelMeter.counterBuilder("FilePageCacheLockFree.totalHeapBytesReclaimed").buildObserver()

    val totalNativeBytesInUse = otelMeter.gaugeBuilder("FilePageCacheLockFree.nativeBytesInUse").ofLongs().buildObserver()
    val totalHeapBytesInUse = otelMeter.gaugeBuilder("FilePageCacheLockFree.heapBytesInUse").ofLongs().buildObserver()

    val totalPagesAllocated = otelMeter.counterBuilder("FilePageCacheLockFree.totalPagesAllocated").buildObserver()
    val totalPagesReclaimed = otelMeter.counterBuilder("FilePageCacheLockFree.totalPagesReclaimed").buildObserver()
    val totalPagesHandedOver = otelMeter.counterBuilder("FilePageCacheLockFree.totalPagesHandedOver").buildObserver()
    val totalPageAllocationsWaited = otelMeter.counterBuilder("FilePageCacheLockFree.totalPageAllocationsWaited").buildObserver()

    val totalBytesRead = otelMeter.counterBuilder("FilePageCacheLockFree.totalBytesRead").buildObserver()
    val totalBytesWritten = otelMeter.counterBuilder("FilePageCacheLockFree.totalBytesWritten").buildObserver()

    val totalPagesWritten = otelMeter.counterBuilder("FilePageCacheLockFree.totalPagesWritten").buildObserver()
    val totalPagesRequested = otelMeter.counterBuilder("FilePageCacheLockFree.totalPagesRequested").buildObserver()

    val totalBytesRequested = otelMeter.counterBuilder("FilePageCacheLockFree.totalBytesRequested").buildObserver()

    val totalPagesRequestsMs = otelMeter.counterBuilder("FilePageCacheLockFree.totalPagesRequestsMs").buildObserver()
    val totalPagesReadMs = otelMeter.counterBuilder("FilePageCacheLockFree.totalPagesReadMs").buildObserver()
    val totalPagesWriteMs = otelMeter.counterBuilder("FilePageCacheLockFree.totalPagesWriteMs").buildObserver()

    val housekeeperTurnsDone = otelMeter.counterBuilder("FilePageCacheLockFree.housekeeperTurnsDone").buildObserver()
    val housekeeperTimeSpentMs = otelMeter.counterBuilder("FilePageCacheLockFree.housekeeperTimeSpentMs").buildObserver()
    val housekeeperTurnsSkipped = otelMeter.counterBuilder("FilePageCacheLockFree.housekeeperTurnsSkipped").buildObserver()

    val totalClosedStoragesReclaimed = otelMeter.counterBuilder("FilePageCacheLockFree.totalClosedStoragesReclaimed").buildObserver()

    otelMeter.batchCallback(
      {
        try {
          StorageLockContext.getNewCacheStatistics()?.let {
            totalNativeBytesAllocated.record(it.totalNativeBytesAllocated())
            totalNativeBytesReclaimed.record(it.totalNativeBytesReclaimed())

            totalHeapBytesAllocated.record(it.totalHeapBytesAllocated())
            totalHeapBytesReclaimed.record(it.totalHeapBytesReclaimed())

            totalHeapBytesInUse.record(it.heapBytesCurrentlyUsed())
            totalNativeBytesInUse.record(it.nativeBytesCurrentlyUsed())

            totalPagesAllocated.record(it.totalPagesAllocated().toLong())
            totalPagesReclaimed.record(it.totalPagesReclaimed().toLong())
            totalPagesHandedOver.record(it.totalPagesHandedOver().toLong())
            totalPageAllocationsWaited.record(it.totalPageAllocationsWaited().toLong())

            totalBytesRead.record(it.totalBytesRead())
            totalBytesWritten.record(it.totalBytesWritten())

            totalPagesWritten.record(it.totalPagesWritten())
            totalPagesRequested.record(it.totalPagesRequested())

            totalBytesRequested.record(it.totalBytesRequested())

            totalPagesRequestsMs.record(it.totalPagesRequests(MILLISECONDS))
            totalPagesReadMs.record(it.totalPagesRead(MILLISECONDS))
            totalPagesWriteMs.record(it.totalPagesWrite(MILLISECONDS))

            totalClosedStoragesReclaimed.record(it.totalClosedStoragesReclaimed().toLong())

            housekeeperTurnsSkipped.record(it.housekeeperTurnsSkipped())
            housekeeperTurnsDone.record(it.housekeeperTurnsDone())
            housekeeperTimeSpentMs.record(it.housekeeperTimeSpent(MILLISECONDS))
          }
        }
        catch (_: AlreadyDisposedException) {

        }
      },
      totalNativeBytesAllocated, totalNativeBytesReclaimed,
      totalHeapBytesAllocated, totalHeapBytesReclaimed,

      totalHeapBytesInUse, totalNativeBytesInUse,

      totalPagesAllocated, totalPagesReclaimed, totalPagesHandedOver, totalPageAllocationsWaited,

      totalBytesRead, totalBytesWritten, totalPagesWritten,

      totalPagesRequested, totalBytesRequested,
      totalPagesRequestsMs, totalPagesReadMs, totalPagesWriteMs,

      totalClosedStoragesReclaimed,

      housekeeperTurnsDone, housekeeperTurnsSkipped, housekeeperTimeSpentMs
    )

  }

  private fun setupFilePageCacheReporting(otelMeter: Meter) {
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
    val directBufferAllocatorTotalSizeCached = otelMeter.gaugeBuilder("DirectByteBufferAllocator.totalSizeOfBuffersCachedInBytes")
      .ofLongs()
      .setUnit("bytes")
      .buildObserver()
    val directBufferAllocatorTotalSizeAllocated = otelMeter.gaugeBuilder("DirectByteBufferAllocator.totalSizeOfBuffersAllocatedInBytes")
      .ofLongs()
      .setUnit("bytes")
      .buildObserver()

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

          directBufferAllocatorTotalSizeCached.record(bufferAllocatorStats.totalSizeOfBuffersCachedInBytes)
          directBufferAllocatorTotalSizeAllocated.record(bufferAllocatorStats.totalSizeOfBuffersAllocatedInBytes)
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
      directBufferAllocatorTotalSizeAllocated, directBufferAllocatorTotalSizeCached
    )
  }
}

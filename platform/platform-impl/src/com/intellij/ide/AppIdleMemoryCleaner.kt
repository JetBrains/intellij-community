// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(FlowPreview::class)

package com.intellij.ide

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.diagnostic.PlatformMemoryUtil
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.impl.isRhizomeProgressEnabled
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.UnindexedFilesScannerExecutor
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeFrame
import com.intellij.platform.ide.progress.activeTasks
import com.intellij.platform.ide.progress.updates
import com.intellij.util.io.DirectByteBufferAllocator
import com.intellij.util.io.StorageLockContext
import com.intellij.util.io.storage.HeavyProcessLatch
import fleet.kernel.rete.asValuesFlow
import fleet.kernel.rete.tokensFlow
import fleet.kernel.tryWithEntities
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus
import java.lang.management.ManagementFactory
import kotlin.system.measureNanoTime

@ApiStatus.Internal
@Service(Service.Level.APP)
class AppIdleMemoryCleaner(private val cs: CoroutineScope) {
  private val isDeactivated: MutableStateFlow<Boolean> = MutableStateFlow(true)
  private val hasActiveBackgroundTasks: StateFlow<Boolean> = cs.hasActiveBackgroundTasksStateFlow()

  private var job: Job? = null

  private var lastCleaningTime: Long = 0L

  init {
    isDeactivated.onEach {
      if (it) {
        job = cs.launch { allWindowsDeactivated() }
      } else {
        job?.cancel()
        job = null
      }
    }.launchIn(cs)
  }

  private class MyApplicationActivationListener : ApplicationActivationListener {
    override fun applicationActivated(ideFrame: IdeFrame) {
      serviceIfCreated<AppIdleMemoryCleaner>()?.apply {
        isDeactivated.value = false
      }
    }

    override fun applicationDeactivated(ideFrame: IdeFrame) {
      if (!Registry.`is`("ide.idle.memory.cleaner.enabled")) return
      if (isModal()) {
        // Modal state hints at some ongoing activity
        return
      }
      service<AppIdleMemoryCleaner>().apply {
        isDeactivated.value = true
      }
    }
  }

  /**
   * The function starts executing when all application windows are deactivated (unfocused)
   * and is canceled when any application window is activated
   */
  @Suppress("SimplifyBooleanWithConstants")
  private suspend fun allWindowsDeactivated() {
    delay(Registry.get("ide.idle.memory.cleaner.delay").asInteger().toLong())
    for (project in ProjectManager.getInstance().openProjects) {
      val scanner = project.serviceIfCreated<UnindexedFilesScannerExecutor>() ?: continue
      scanner.isRunning.first { it == false }
    }
    hasActiveBackgroundTasks.first { it == false }
    while (hasOngoingHighlightingInAnyProject() || HeavyProcessLatch.INSTANCE.isRunning) {
      // We could use listeners instead, but it looks overkill in this case - we're not in a hurry
      delay(1000)
    }

    val now = System.nanoTime()
    val durationSinceLastCleanup = if (lastCleaningTime > 0) now - lastCleaningTime else null
    if (durationSinceLastCleanup != null && durationSinceLastCleanup < 60_000_000_000) return
    lastCleaningTime = now

    MemoryCleaningStats(durationSinceLastCleanup).use { stats ->
      performCleanup(stats)

      delay(1000) // Wait for JVM to decommit heap regions
      stats.logToFus()
    }
  }

  private fun performCleanup(stats: MemoryCleaningStats) {
    LOG.debug("Performing memory cleanup")
    stats.measureGc {
      runGc()
    }
    stats.measureDirectBuffers {
      releaseIndexCachedDirectBuffers()
    }

    PlatformMemoryUtil.getInstance().trimLinuxNativeHeap()
  }

  private fun releaseIndexCachedDirectBuffers() {
    // This cache is allocated off-heap and can be quickly retuned to the OS.
    // Also, this cache is short-living: it takes little time to allocate and fill it back.
    StorageLockContext.forceDirectMemoryCache()
    DirectByteBufferAllocator.ALLOCATOR.releaseCachedBuffers()
  }

  private fun runGc() {
    // `System.gc()` makes the heap to shrink respecting `-XX:MaxHeapFreeRatio` VM option, so some memory is returned to OS.
    // At the same time, it leads to a long GC pause (hundreds of milliseconds). This should be OK since the IDE is idle.
    System.gc()
  }
}

private val LOG: Logger = logger<AppIdleMemoryCleaner>()

private suspend fun hasOngoingHighlightingInAnyProject(): Boolean {
  return readAction {
    ProjectManager.getInstance().openProjects.any { project ->
      DaemonCodeAnalyzer.getInstance(project).isRunning
    }
  }
}

private fun isModal(): Boolean {
  return LaterInvocator.isInModalContext() || ProgressManager.getInstance().hasModalProgressIndicator()
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun CoroutineScope.hasActiveBackgroundTasksStateFlow(): StateFlow<Boolean> {
  if (!isRhizomeProgressEnabled) {
    return MutableStateFlow(false)
  }
  return activeTasks.asValuesFlow().flatMapMerge { task ->
    flow {
      emit(1)
      tryWithEntities(task) { task.updates.tokensFlow().collect {} }
      emit(-1)
    }
  }
    .scan(0) { acc, delta -> acc + delta }
    .map { count -> count > 0 }
    .debounce { if (it) 0 else 2000 }
    .stateIn(this, SharingStarted.Eagerly, false)
}

internal object AppIdleMemoryCleanerUsagesCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("ide.idle.memory.cleaner", 1)

  private val XMX = EventFields.BoundedInt("xmx", intArrayOf(512, 768, 1024, 1536, 2048, 4096, 6000, 8192, 12288, 16384))
  private val DURATION_SINCE_LAST_CLEANUP = EventFields.Long("duration_since_last_cleanup_ms", description = "Duration since last event")
  private val GC_DURATION = EventFields.Int("gc_duration_ms", description = "Duration of GC invocation")
  private val MEM_BEFORE_CLEANUP_MB = EventFields.Int(
    "mem_before_cleanup_mb", description = "OS-provided process memory usage (`RAM + SWAP - FileMappings`) before cleanup")
  private val MEM_AFTER_CLEANUP_MB = EventFields.Int(
    "mem_after_cleanup_mb", description = "OS-provided process memory usage (`RAM + SWAP - FileMappings`) after cleanup")
  private val TOTAL_CLEANED_MB = EventFields.Int(
    "total_cleaned_mb", description = "mem_before_cleanup_mb - mem_after_cleanup_mb")
  private val TOTAL_CLEANED_PERCENT = EventFields.Int(
    "total_cleaned_percent", description = "total_cleaned_mb * 100 / mem_before_cleanup_mb")
  private val GC_CLEANED_MB = EventFields.Int("gc_cleaned_mb")
  private val DIRECT_BUFFERS_CLEANED_MB = EventFields.Int("direct_buffers_cleaned_mb")
  private val MEMORY_CLEANUP_PERFORMED = GROUP.registerVarargEvent(
    "memory.cleanup.performed",
    XMX,
    DURATION_SINCE_LAST_CLEANUP,
    MEM_BEFORE_CLEANUP_MB,
    MEM_AFTER_CLEANUP_MB,
    GC_DURATION,
    TOTAL_CLEANED_MB,
    TOTAL_CLEANED_PERCENT,
    GC_CLEANED_MB,
    DIRECT_BUFFERS_CLEANED_MB,
  )

  override fun getGroup(): EventLogGroup = GROUP

  internal fun memoryCleanupPerformed(
    durationSinceLastCleanupMs: Long,
    gcDurationMs: Long,
    memBeforeCleanupMb: Long,
    memAfterCleanupMb: Long,
    gcCleanedMb: Long,
    directBuffersCleanedMb: Long,
  ) {
    val xmxMb = ManagementFactory.getMemoryMXBean().heapMemoryUsage.max.toMB()
    val totalCleanedMb = memBeforeCleanupMb - memAfterCleanupMb
    MEMORY_CLEANUP_PERFORMED.log(
      XMX.with(xmxMb.toInt()),
      DURATION_SINCE_LAST_CLEANUP.with(durationSinceLastCleanupMs),
      GC_DURATION.with(gcDurationMs.toInt()),
      MEM_BEFORE_CLEANUP_MB.with(memBeforeCleanupMb.toInt()),
      MEM_AFTER_CLEANUP_MB.with(memAfterCleanupMb.toInt()),
      TOTAL_CLEANED_MB.with(totalCleanedMb.toInt()),
      TOTAL_CLEANED_PERCENT.with((totalCleanedMb * 100 / memBeforeCleanupMb).toInt()),
      GC_CLEANED_MB.with(gcCleanedMb.toInt()),
      DIRECT_BUFFERS_CLEANED_MB.with(directBuffersCleanedMb.toInt()),
    )
  }
}

private class MemoryCleaningStats(
  private val durationSinceLastCleanupNanos: Long?,
) : AutoCloseable {
  private val memStatsProvider: PlatformMemoryUtil.MemoryStatsProvider = PlatformMemoryUtil.getInstance().newMemoryStatsProvider()
  private val memoryUsageBytesBeforeCleanup: Long = getTotal2MemoryUsage()
  private var gcDurationNanos: Long = -1
  private var gcCleanedMb: Long = -1
  private var directBuffersCleanedMb: Long = -1

  inline fun measureGc(action: () -> Unit) {
    val oldUsage = getCommittedHeapBytes()
    gcDurationNanos = measureNanoTime { action() }
    gcCleanedMb = (oldUsage - getCommittedHeapBytes()).toMB()
  }

  inline fun measureDirectBuffers(action: () -> Unit) {
    val oldUsage = getDirectBuffersAllocatedBytes()
    action()
    directBuffersCleanedMb = (oldUsage - getDirectBuffersAllocatedBytes()).toMB()
  }

  fun getDirectBuffersAllocatedBytes(): Long {
    return DirectByteBufferAllocator.ALLOCATOR.statistics.totalSizeOfBuffersAllocatedInBytes
  }

  private fun getCommittedHeapBytes(): Long {
    return ManagementFactory.getMemoryMXBean().heapMemoryUsage.committed
  }

  private fun getTotal2MemoryUsage(): Long {
    return memStatsProvider.getCurrentProcessMemoryStats()?.ramPlusSwapMinusFileMappings ?: 0
  }

  fun logToFus() {
    val memoryUsageBytesAfterCleanup = getTotal2MemoryUsage()
    AppIdleMemoryCleanerUsagesCollector.memoryCleanupPerformed(
      durationSinceLastCleanupMs = durationSinceLastCleanupNanos?.let { it / 1_000_000 } ?: -1,
      gcDurationMs = gcDurationNanos / 1_000_000,
      memBeforeCleanupMb = memoryUsageBytesBeforeCleanup.toMB(),
      memAfterCleanupMb = memoryUsageBytesAfterCleanup.toMB(),
      gcCleanedMb = gcCleanedMb,
      directBuffersCleanedMb = directBuffersCleanedMb,
    )

    val totalCleanedMb = (memoryUsageBytesBeforeCleanup - memoryUsageBytesAfterCleanup).toMB()
    LOG.debug { "Performed memory cleanup. Cleaned: $totalCleanedMb MB" }
  }

  override fun close() {
    memStatsProvider.close()
  }
}

private fun Long.toMB() = this / 1024 / 1024

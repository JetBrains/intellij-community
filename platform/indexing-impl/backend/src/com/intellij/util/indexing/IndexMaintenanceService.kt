// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.util.indexing

import com.intellij.ide.IdleTracker
import com.intellij.ide.PowerSaveMode
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.util.PotemkinProgress
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.UnindexedFilesScannerExecutor
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.RefreshQueueImpl
import com.intellij.platform.util.io.storages.database.spi.metrics.DatabaseMetrics
import com.intellij.util.SystemProperties.getFloatProperty
import com.intellij.util.SystemProperties.getIntProperty
import com.intellij.util.indexing.impl.storage.IndexMaintenance
import com.intellij.util.indexing.impl.storage.durablemap.database.DurableDatabaseIndexLayoutProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private val LOG = logger<IndexMaintenanceService>()

/** How soon to start first maintenance round, after user gets AFK */
private val IDLE_PERIOD_TO_START_MAINTENANCE = getIntProperty("ide.index.maintenance.idle-period-to-start-sec", 1200).seconds
/** While user still AFK, each next maintenance round delayed this times longer than previous one */
private val MAINTENANCE_INTERVAL_MULTIPLIER = getFloatProperty("ide.index.maintenance.period-multiplier", 1.5f)
                                                .toDouble()
                                                .takeIf { it.isFinite() && it >= 1.0 } ?: 1.5

/**
 * High-level maintenance control for [DurableDatabaseIndexLayoutProvider]: runs some compaction work while user is AFK,
 * and no other heavy-tasks are running.
 *
 * Maintenance loop starts when [DurableDatabaseIndexLayoutProvider] is [registerForMaintenance]-ed, and runs while it is registered.
 */
@ApiStatus.Internal
class IndexMaintenanceService(scope: CoroutineScope) : IndexMaintenance<DurableDatabaseIndexLayoutProvider> {
  private val lock = Any()
  private var activeProvider: DurableDatabaseIndexLayoutProvider? = null
  /**
   * During the maintenance round the [DurableDatabaseIndexLayoutProvider] could be closed/recreated, leaving [activeProvider] empty.
   * To not occasionally disable the maintenance because of that, we set `maintenanceInProgress=true` during this gap.
   */
  private var maintenanceInProgress = false

  /** Key metrics to make decision about maintenance, for current provider */
  private var providerBaselineMetrics: MaintenanceMetrics? = null

  private val maintenanceEnabled = MutableStateFlow(false)

  val hasActiveProvider: Boolean
    get() = synchronized(lock) { activeProvider != null }

  init {
    val app = ApplicationManager.getApplication()
    if (app.isUnitTestMode || app.isHeadlessEnvironment) {
      LOG.info("OnIdle index maintenance disabled in a test or headless environment")
    }
    else {
      scope.launch(Dispatchers.EDT) {
        maintenanceEnabled.collectLatest { enabled ->
          if (enabled) {
            LOG.info("OnIdle index maintenance: starts after ${IDLE_PERIOD_TO_START_MAINTENANCE.inWholeSeconds} sec idle, " +
                     "each next round ${MAINTENANCE_INTERVAL_MULTIPLIER}x later")
            try {
              runMaintenanceOnIdle(
                IdleTracker.getInstance().events,
                IDLE_PERIOD_TO_START_MAINTENANCE,
                MAINTENANCE_INTERVAL_MULTIPLIER,
                backgroundActivities = ::fetchBackgroundActivity,
                powerSaveMode = PowerSaveMode::isEnabled,
                maintenance = ::runMaintenanceWithProgress
              )
            }
            finally {
              LOG.info("OnIdle index maintenance stopped")
            }
          }
        }
      }
    }
  }

  override fun registerForMaintenance(provider: DurableDatabaseIndexLayoutProvider): AutoCloseable {
    synchronized(lock) {
      check(activeProvider == null) { "Provider is already registered: $activeProvider" }
      activeProvider = provider
      providerBaselineMetrics = null
      updateEnabledLocked()
    }
    return AutoCloseable {
      synchronized(lock) {
        if (activeProvider === provider) {
          activeProvider = null
          providerBaselineMetrics = null
        }
        updateEnabledLocked()
      }
    }
  }

  /** Releases the lock before the action can close or replace a provider */
  fun tryRunMaintenance(expectedProvider: DurableDatabaseIndexLayoutProvider? = null, action: () -> Unit): Boolean {
    synchronized(lock) {
      if (activeProvider == null || maintenanceInProgress ||
          (expectedProvider != null && activeProvider !== expectedProvider)) return false
      maintenanceInProgress = true
      updateEnabledLocked()
    }
    try {
      action()
      return true
    }
    finally {
      synchronized(lock) {
        maintenanceInProgress = false
        updateEnabledLocked()
      }
    }
  }

  /** Shows the modal progress while the tumbler consumes EDT */
  private suspend fun runMaintenanceWithProgress(): Boolean {
    val provider = synchronized(lock) { activeProvider } ?: return false
    val metrics = withContext(Dispatchers.Default) { provider.databaseMetrics() }
    val previous = synchronized(lock) { providerBaselineMetrics }
    if (metrics === DatabaseMetrics.DUMMY) {
      LOG.info("Maintenance skipped: database metrics are unavailable")
      return false
    }
    val current = metrics.forMaintenance()
    if (!shouldRunMaintenance(current, previous)) {
      if (current.sealedChunks == 0 && current.retiredChunks == 0) {
        LOG.info("Maintenance skipped: no sealed or retired chunks")
      }
      else if (previous != null) {
        LOG.info("Maintenance skipped: sealed chunks=${current.sealedChunks}; since the previous round, " +
                 "created chunks=${current.createdChunks - previous.createdChunks}, " +
                 "allocated blocks=${current.allocatedBlocks - previous.allocatedBlocks}, " +
                 "retired blocks=${current.retiredBlocks - previous.retiredBlocks}; " +
                 "block threshold=${blockGrowthThreshold(previous.totalBlocks)}")
      }
      return false
    }
    val title = IndexingBundle.message("indexes.defragmentation.progress.title")
    val progress = PotemkinProgress(title, null, null, null) //use PotemkinProgress because we block EDT
    progress.setDelayInMillis(0)
    progress.isIndeterminate = true
    var completed = false
    progress.runInSwingThread {
      progress.text2 = IndexingBundle.message("indexes.defragmentation.progress.closing")
      progress.interact()
      completed = tryRunMaintenance(provider) {
        val tumbler = FileBasedIndexTumbler("Idle index maintenance")
        try {
          tumbler.turnOffLightweight()
        }
        finally {
          // Restore the indices before cancellation can interrupt the maintenance cycle
          try {
            progress.text2 = IndexingBundle.message("indexes.defragmentation.progress.opening")
            progress.interact()
          }
          finally {
            //here is the actual compaction happens: on opening index DB does a compaction round
            tumbler.turnOnLightweight()
          }
        }
      }
    }
    if (completed) {
      val currentProvider = synchronized(lock) { activeProvider }
      val metrics = withContext(Dispatchers.Default) { currentProvider?.databaseMetrics() }
      synchronized(lock) {
        providerBaselineMetrics = if (currentProvider != null && currentProvider === activeProvider && metrics != null && metrics !== DatabaseMetrics.DUMMY) {
          metrics.forMaintenance()
        }
        else null
      }
    }
    return completed
  }

  private fun fetchBackgroundActivity(): BackgroundActivities {
    var activity: String? = null
    val counters = HashMap<ModificationTracker, Long>()
    val fileManager = VirtualFileManager.getInstance()
    counters[fileManager] = fileManager.modificationCount
    if (RefreshQueueImpl.isRefreshInProgress || RefreshQueueImpl.isEventProcessingInProgress) {
      activity = "VFS refresh"
    }
    for (project in ProjectUtil.getOpenProjects()) {
      if (project.isDisposed) continue
      val scanner = UnindexedFilesScannerExecutor.getInstance(project)
      val dumbService = DumbService.getInstance(project)
      // Scans that schedule indexing advance the dumb-mode tracker instead
      counters[scanner.modificationTracker] = scanner.modificationTracker.modificationCount
      counters[dumbService.modificationTracker] = dumbService.modificationTracker.modificationCount
      if (activity == null && (scanner.isRunning.value || scanner.hasQueuedTasks)) {
        activity = "scanning in project '${project.name}'"
      }
      if (activity == null && dumbService.isDumb) {
        activity = "indexing in project '${project.name}'"
      }
    }
    return BackgroundActivities(activity, counters)
  }

  private fun updateEnabledLocked() {
    maintenanceEnabled.value = activeProvider != null || maintenanceInProgress
  }
}

@VisibleForTesting
@ApiStatus.Internal
data class MaintenanceMetrics(
  val sealedChunks: Int,
  val retiredChunks: Int,
  val totalBlocks: Int,
  val allocatedBlocks: Long,
  val retiredBlocks: Long,
  val createdChunks: Long,
  val evacuatedBlocks: Long,
)

/** Extract few metrics important for maintenance decision-making */
private fun DatabaseMetrics.forMaintenance() = MaintenanceMetrics(
  sealedChunks = chunks().sealedCurrent(),
  retiredChunks = chunks().retiredCurrent(),
  totalBlocks = blocks().total(),
  allocatedBlocks = blocks().allocated(),
  retiredBlocks = blocks().retired(),
  createdChunks = chunks().created(),
  evacuatedBlocks = blocks().evacuated(),
)

private fun blockGrowthThreshold(totalBlocks: Int): Long = maxOf(1L, (totalBlocks.toLong() + 9) / 10)

/** Compares current database metrics against (previously stored) baseline, to heuristically decide: is maintenance a good idea? */
@VisibleForTesting
@ApiStatus.Internal
fun shouldRunMaintenance(current: MaintenanceMetrics, baseline: MaintenanceMetrics?): Boolean {
  if (current.sealedChunks == 0 && current.retiredChunks == 0) return false  // (likely) nothing to compact

  if (baseline == null) return true                                          // nothing to compare against: do maintenance conservatively
  if (current.retiredChunks > 0) return true                                 // worth to delete retired chunk
  if (baseline.evacuatedBlocks > 0) return true                              // (probably) there is more to evacuate

  // growth/churn rate is big enough:
  if ( (current.createdChunks - baseline.createdChunks) > 0) return true
  val threshold = blockGrowthThreshold(baseline.totalBlocks)                 // ~=10% if total blocks
  if ( (current.allocatedBlocks - baseline.allocatedBlocks) >= threshold) return true
  return (current.retiredBlocks - baseline.retiredBlocks) >= threshold
}

/** Keeps counters for different modification trackers */
@ApiStatus.Internal
data class BackgroundActivities(val description: String? = null, val counters: Map<ModificationTracker, Long> = emptyMap())

/**
 * Long-running task: runs maintenance rounds if there is long enough period of idleness: no user activity, and no important background
 * IDE tasks;
 * As long as IDE keeps idle, it runs next maintenance rounds, but each following round runs after progressively longer delay.
 */
@VisibleForTesting
@ApiStatus.Internal
suspend fun runMaintenanceOnIdle(
  edtEventFinished: Flow<Unit>,
  initialIdleDelay: Duration,
  delayMultiplier: Double = 1.5,
  backgroundActivities: () -> BackgroundActivities = { BackgroundActivities() },
  timeSource: TimeSource = TimeSource.Monotonic,
  powerSaveMode: () -> Boolean = { false },
  maintenance: suspend () -> Boolean,
): Unit = coroutineScope {
  require(delayMultiplier.isFinite() && delayMultiplier >= 1.0) {
    "The maintenance interval multiplier(=$delayMultiplier) must be finite and >=1"
  }

  var lastUserActivity = timeSource.markNow()
  var repeatDelay = initialIdleDelay
  launch {
    edtEventFinished.collect {
      lastUserActivity = timeSource.markNow()
      repeatDelay = initialIdleDelay
    }
  }

  // Trying to run a maintenance when nothing else runs:
  // 1. No user (EDT) activity for long enough (initialIdleDelay)
  // 2. No important IDE background activity for at least some time (1-2 checking periods)
  // (Checking period is longer in power saving mode to save battery)
  //
  // If an IDE continues to be idle of user activity -- maintenance runs again, but at progressively longer delays (delayMultiplier)
  // => because without user activity repeating maintenance rounds likely provide diminishing returns.

  var lastMaintenanceCompletedAt: TimeMark? = null
  var previousActivity = backgroundActivities()
  LOG.info("Idle maintenance tracking started: first AFK delay=$initialIdleDelay, next x$delayMultiplier")
  while (currentCoroutineContext().isActive) {
    delay(if (powerSaveMode()) 5.minutes else 1.minutes)

    val activity = backgroundActivities()
    val backgroundWasQuiet = previousActivity.description == null && activity.description == null &&
                             previousActivity.counters == activity.counters
    val activityReason = activity.description ?: previousActivity.description ?: "background activity counter changed"
    // Update the snapshot even while the user is active or the repeat delay has not elapsed
    previousActivity = activity

    if (lastUserActivity.elapsedNow() < initialIdleDelay) continue

    val sincePreviousMaintenance = lastMaintenanceCompletedAt?.elapsedNow()
    if (sincePreviousMaintenance != null && sincePreviousMaintenance < repeatDelay) continue

    if (!backgroundWasQuiet) {
      LOG.info("User is AFK, but maintenance postponed because of [$activityReason]")
      continue
    }

    val startedAt = timeSource.markNow()
    val userActivityBeforeMaintenance = lastUserActivity
    LOG.info("Maintenance round started")
    if (!maintenance()) {
      LOG.info("Maintenance round skipped")
      continue
    }
    lastMaintenanceCompletedAt = timeSource.markNow()
    // The modal progress can process user events during maintenance
    if (lastUserActivity == userActivityBeforeMaintenance) {
      repeatDelay *= delayMultiplier
    }
    LOG.info("Maintenance round completed in ${startedAt.elapsedNow().inWholeMilliseconds}ms; next round in $repeatDelay")
  }
}

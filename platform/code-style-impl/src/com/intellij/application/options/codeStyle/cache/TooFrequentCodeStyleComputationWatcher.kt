// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.cache

import com.intellij.diagnostic.ThreadDumper
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class TooFrequentCodeStyleComputationWatcher(val project: Project) {
  companion object {
    private val LOG: Logger = thisLogger()

    @JvmStatic
    fun getInstance(project: Project): TooFrequentCodeStyleComputationWatcher = project.service()
  }

  @Volatile
  private var cacheEvictionTrackingStart: Long = TRACKING_NOT_STARTED
  private var lastCacheAccessTime: Long = System.currentTimeMillis()
  private var settingsModCountAtTrackingStart: Long = -1

  // average duration between cache evictions in millis
  private val rollingAvgBuffer = RollingAvgBuffer(
    ROLLING_AVG_WINDOW_SIZE,
    // some large enough value that does not trigger automatic recovery but does not overflow the rolling sum
    //  -> 10x threshold
    (1.0 / HIGH_CACHE_EVICTION_RATE_THRESHOLD * 1000 * 10).toLong()
  )

  private fun calcCurrentCacheEvictionRate(): Double = 1.0 / rollingAvgBuffer.rollingAvg * 1000

  fun beforeCacheEntryInserted(currentCacheSize: Int, maxCacheSize: Int) {
    if (Registry.`is`("code.style.cache.high.eviction.rate.automatic.recovery.enabled") && !isEvictionTrackingBlocked()) {
      synchronized(this) { checkEvictionRate(currentCacheSize, maxCacheSize) }
    }
  }

  private fun getCurrentProjectSettingsModCount(): Long =
    @Suppress("DEPRECATION")
    CodeStyleSettingsManager.getInstance().getCurrentSettings().modificationTracker.modificationCount

  private fun checkEvictionRate(currentCacheSize: Int, maxCacheSize: Int) {
    val currentTime = System.currentTimeMillis()
    if (!isEvictionTrackingBlocked() && currentCacheSize >= maxCacheSize) {
      rollingAvgBuffer.update(currentTime - lastCacheAccessTime)
      val cacheEvictionRate = calcCurrentCacheEvictionRate()
      if (cacheEvictionRate > HIGH_CACHE_EVICTION_RATE_THRESHOLD) {
        if (cacheEvictionTrackingStart == TRACKING_NOT_STARTED) {
          cacheEvictionTrackingStart = currentTime
          settingsModCountAtTrackingStart = getCurrentProjectSettingsModCount()
          LOG.info("High cache eviction rate ($cacheEvictionRate), tracking started")
        }
        else {
          val evictionTrackingDuration = currentTime - cacheEvictionTrackingStart
          if (evictionTrackingDuration > HIGH_CACHE_EVICTION_RATE_MIN_DURATION) {
            val dump = dumpState(cacheEvictionRate)
            val attachment = Attachment("codeStyleCacheDump.txt", dump)
            val threadDump = ThreadDumper.dumpThreadsToString()
            val attachment2 = Attachment("threadDump.txt", threadDump)
            LOG.error("Too high code style cache eviction rate detected.", attachment, attachment2)
            stopEvictionTracking(true, cacheEvictionRate)
          }
        }
      }
      else {
        stopEvictionTracking(false, cacheEvictionRate)
      }
    }
    else {
      stopEvictionTracking(false, null)
    }
    lastCacheAccessTime = currentTime
  }

  @Synchronized
  internal fun dumpState(currentEvictionRate: Double): String {
    val sb = StringBuilder()
    sb.appendLine("Too high cache eviction rate detected")
    sb.appendLine("Opened editors:")
    EditorFactory.getInstance().editorList
      .filter { it.project == project }
      .forEach { sb.appendLine(it.toString()) }
    sb.appendLine("Project settings mod count at the start of the tracking: $settingsModCountAtTrackingStart")
    sb.appendLine("Current project settings mod count: ${getCurrentProjectSettingsModCount()}")
    sb.appendLine("Cache eviction tracking started at: $cacheEvictionTrackingStart")
    sb.appendLine("Current time: ${System.currentTimeMillis()}")
    sb.appendLine("Cache eviction tracking duration: ${System.currentTimeMillis() - cacheEvictionTrackingStart}")
    sb.appendLine("Cache eviction rate: ${currentEvictionRate} evictions per second")
    (CodeStyleCachingService.getInstance(project) as? CodeStyleCachingServiceImpl)?.dumpState(sb)
    return sb.toString()
  }

  // A high eviction rate for a significant amount of time most likely means that the code style settings are being computed in a loop.
  // In these cases, the IDE will use a lot of CPU and may be overloaded and unusable (the same way as in IJPL-179136).
  fun isTooHighEvictionRateDetected(): Boolean = isEvictionTrackingBlocked()

  private fun isEvictionTrackingBlocked(): Boolean {
    return cacheEvictionTrackingStart == TRACKING_BLOCKED
  }

  private fun stopEvictionTracking(blockTracking: Boolean, rate: Double?) {
    require(!isEvictionTrackingBlocked()) { "Tracking is already blocked" }
    if (blockTracking) {
      cacheEvictionTrackingStart = TRACKING_BLOCKED
      LOG.info("High cache eviction rate (${rate}): tracking stopped and blocked")
      return
    }
    if (cacheEvictionTrackingStart != TRACKING_NOT_STARTED) {
      cacheEvictionTrackingStart = TRACKING_NOT_STARTED
      LOG.info("High cache eviction rate ($rate): tracking stopped")
    }
  }
}

// bulk operations (e.g., reformatting a directory) may exceed the eviction rate threshold for their duration
private val HIGH_CACHE_EVICTION_RATE_MIN_DURATION: Long
  get() = Registry.intValue("code.style.cache.high.eviction.rate.automatic.recovery.threshold.duration").toLong()

// evictions-per-second
private val HIGH_CACHE_EVICTION_RATE_THRESHOLD: Long
  get() = Registry.intValue("code.style.cache.high.eviction.rate.automatic.recovery.threshold.frequency").toLong()

private const val TRACKING_NOT_STARTED: Long = -1
private const val TRACKING_BLOCKED: Long = -2

private const val ROLLING_AVG_WINDOW_SIZE: Int = 100


private class RollingAvgBuffer(private val windowSize: Int, initialBufferValue: Long) {
  private var buffer: Array<Long> = Array(windowSize) { initialBufferValue }
  private var i: Int = 0
  private var rollingSum: Long = buffer.sum()
  var rollingAvg: Double = rollingSum.toDouble() / windowSize
    private set

  fun update(value: Long) {
    rollingSum -= buffer[i]
    rollingSum += value
    buffer[i] = value
    i = (i + 1) % windowSize
    rollingAvg = rollingSum.toDouble() / windowSize
  }
}

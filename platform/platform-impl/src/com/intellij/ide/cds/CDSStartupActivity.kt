// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.cds

import com.intellij.ide.PowerSaveMode
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.NonUrgentExecutor
import com.intellij.util.io.PowerStatus
import com.sun.management.OperatingSystemMXBean
import java.lang.management.ManagementFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

private val LOG = logger<CDSStartupActivity>()

internal class CDSStartupActivity : StartupActivity.DumbAware {
  private val isExecuted = AtomicBoolean(false)

  //for tests
  val setupResult = AtomicReference<String>(null)

  override fun runActivity(project: Project) {
    if (!isExecuted.compareAndSet(false, true) || !Registry.`is`("appcds.useStartupActivity")) {
      return
    }

    NonUrgentExecutor.getInstance().execute {
      if (!CDSManager.isValidEnv && !Registry.`is`("appcds.assumeValidEnv")) {
        return@execute
      }

      val cdsEnabled = Registry.`is`("appcds.enabled")
      val cdsArchive = CDSManager.currentCDSArchive
      if (cdsArchive != null && cdsArchive.isFile) {
        Logger.getInstance(CDSManager::class.java).warn("Running with enabled CDS $cdsArchive, ${StringUtil.formatFileSize(cdsArchive.length())}")
        CDSFUSCollector.logCDSStatus(cdsEnabled, true)
      }
      else {
        CDSFUSCollector.logCDSStatus(cdsEnabled, false)
      }

      // let's execute CDS archive building on the second start of the IDE only,
      // the first run - we set the property, the second run we run it!
      val cdsOnSecondStartKey = "appcds.runOnSecondStart"
      if (Registry.`is`(cdsOnSecondStartKey)) {
        val propertiesComponent = PropertiesComponent.getInstance()
        val hash = CDSPaths.current.cdsClassesHash
        if (propertiesComponent.getValue(cdsOnSecondStartKey) != hash) {
          propertiesComponent.setValue(cdsOnSecondStartKey, hash)
          return@execute
        }
      }

      AppExecutorUtil.getAppExecutorService().submit {
        CDSManager.cleanupStaleCDSFiles(cdsEnabled)
        if (!cdsEnabled) {
          CDSManager.removeCDS()
          setupResult.set("removed")
        }
      }

      if (cdsEnabled) {
        BuildCDSWhenPossibleAction(setupResult).start()
      }
    }
  }

  private enum class WaitOutcome {
    NO_WAIT_NEEDED,
    CHECK_AGAIN,
    TOO_HIGH_CPU_LOAD,
    WAIT_LONG,
  }

  private class BuildCDSWhenPossibleAction(val setupResult: AtomicReference<String>) : Runnable {
    private val osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean::class.java)
    private val cpuCount = osBean.availableProcessors
    private val processCpuLoad by AverageCPULoad { osBean.processCpuLoad }
    private val systemCpuLoad by AverageCPULoad { osBean.systemCpuLoad }
    private val waitIdle get() = Registry.`is`("appcds.install.idleTestEnabled")

    fun start() {
      if (waitIdle) reschedule(WaitOutcome.WAIT_LONG) else run()
    }

    fun reschedule(outcome: WaitOutcome): Boolean {
      val time = when (outcome) {
        WaitOutcome.NO_WAIT_NEEDED -> return false
        WaitOutcome.CHECK_AGAIN -> Registry.intValue("appcds.install.idleRecheckDelay").toLong()
        WaitOutcome.TOO_HIGH_CPU_LOAD -> Registry.intValue("appcds.install.idleTooHeavyCPULoadDelay").toLong()
        WaitOutcome.WAIT_LONG -> Registry.intValue("appcds.install.idleLongRetryDelay").toLong()
      }

      AppExecutorUtil.getAppScheduledExecutorService().schedule(this, time, TimeUnit.MILLISECONDS)
      return true
    }

    fun needsWaitingForIdleMillis(): WaitOutcome = runCatching {
      if (!waitIdle) return WaitOutcome.NO_WAIT_NEEDED

      if (PowerSaveMode.isEnabled()) return WaitOutcome.WAIT_LONG
      if (PowerStatus.getPowerStatus() == PowerStatus.BATTERY) return WaitOutcome.WAIT_LONG

      val processCPULoad = processCpuLoad
      val systemCPULoad = systemCpuLoad
      if (processCPULoad == null || systemCPULoad == null) return WaitOutcome.CHECK_AGAIN

      // check if our IDE is busy right now doing something useful
      // careful! Our CDS archive generation process would take 1 CPU
      // the supported minimum is 2 CPU!
      if (processCPULoad * cpuCount > 2.5) return WaitOutcome.TOO_HIGH_CPU_LOAD
      // OS CPU load, our CDS generation will consume 1 CPU anyways
      if (systemCPULoad * cpuCount > 3.7) return WaitOutcome.TOO_HIGH_CPU_LOAD

      // ensure free space under CDS folder (aka System folder)
      if (CDSPaths.freeSpaceForCDS < 3L * 1024 * 1024 * 1024) return WaitOutcome.WAIT_LONG
      return WaitOutcome.NO_WAIT_NEEDED
    }.getOrElse { WaitOutcome.WAIT_LONG }

    fun canStillWork() = needsWaitingForIdleMillis() <= WaitOutcome.CHECK_AGAIN

    override fun run() {
      val timeToSleep = needsWaitingForIdleMillis()
      if (reschedule(timeToSleep)) return

      CDSManager.installCDS(canStillWork = this::canStillWork, onResult = { result ->
        if (result is CDSTaskResult.InterruptedForRetry) {
          LOG.info("CDS archive generation paused due to high CPU load and will be re-scheduled later")
          reschedule(WaitOutcome.WAIT_LONG)
        }
        else {
          setupResult.set("enabled:" + result.statusName)
        }
      })
    }
  }

  private class AverageCPULoad(val compute: () -> Double) {
    companion object {
      private const val minMetricsTimeDifference = 100
      private const val minMeasurementTime = 2_000
      private const val maxMeasurementTime = 5_000
      private const val minMeasurementsCount = 3
      private const val samples = 128
    }

    private var index = 0
    private val measurements = DoubleArray(samples)
    private val times = LongArray(samples)

    private fun now() = System.currentTimeMillis()

    //helper function for Kotlin delegated property syntax
    operator fun getValue(x: Any?, p: Any): Double? = nextValue()

    // returns null or a measured average CPU load value
    private fun nextValue(): Double? {
      val now = now()

      val nextResult = runCatching { compute() }.getOrNull() ?: return null
      if (nextResult.isNaN() || nextResult < 0.0 || nextResult > 1.0) return null

      // let's skip measurements if they happen too often
      val previousRecord = (index + samples - 1) % samples
      if (now - times[previousRecord] < minMetricsTimeDifference) return null

      measurements[index] = nextResult
      times[index] = now
      index = (index + 1) % samples

      var sum = 0.0
      var count = 0
      var maxDiff = 0L
      // we consider only measurements that were collected within latest 5 minutes
      measurements.indices.forEach { idx ->
        val time = now - times[idx]
        if (time < maxMeasurementTime) {
          maxDiff = max(maxDiff, time)
          sum += measurements[idx]
          count++
        }
      }

      if (maxDiff < minMeasurementTime || count < minMeasurementsCount) return null
      return sum / count
    }
  }
}

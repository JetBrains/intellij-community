// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

private val LOG = logger<CDSStartupActivity>()

internal class CDSStartupActivity : StartupActivity {
  private val isExecuted = AtomicBoolean(false)

  //for tests
  val setupResult = AtomicReference<String>(null)

  override fun runActivity(project: Project) {
    if (!isExecuted.compareAndSet(false, true)) return
    if (!Registry.`is`("intellij.appcds.useStartupActivity", true)) return

    NonUrgentExecutor.getInstance().execute {
      if (!CDSManager.isValidEnv && !Registry.`is`("intellij.appcds.assumeValidEnv", false)) return@execute

      // let's execute CDS archive building on the second start of the IDE only,
      // the first run - we set the property, the second run we run it!
      val cdsOnSecondStartKey = "intellij.appcds.runOnSecondStart"
      if (Registry.`is`(cdsOnSecondStartKey, true)) {
        val propertiesComponent = PropertiesComponent.getInstance()
        val hash = CDSPaths.current.cdsClassesHash
        if (propertiesComponent.getValue(cdsOnSecondStartKey) != hash) {
          propertiesComponent.setValue(cdsOnSecondStartKey, hash)
          return@execute
        }
      }

      val cdsArchive = CDSManager.currentCDSArchive
      if (cdsArchive != null && cdsArchive.isFile) {
        Logger.getInstance(CDSManager::class.java).warn("Running with enabled CDS $cdsArchive, ${StringUtil.formatFileSize(cdsArchive.length())}")
        CDSFUSCollector.logRunningWithCDS()
      }

      val cdsEnabled = Registry.`is`("intellij.appcds.enabled")
      AppExecutorUtil.getAppExecutorService().submit {
        CDSManager.cleanupStaleCDSFiles(cdsEnabled)
        if (!cdsEnabled) {
          CDSManager.removeCDS()
          setupResult.set("removed")
        }
      }

      if (!cdsEnabled) {
        CDSFUSCollector.logCDSDisabled()
      } else {
        CDSFUSCollector.logCDSEnabled()
        BuildCDSWhenNeededAction(setupResult).run()
      }
    }
  }

  private class BuildCDSWhenNeededAction(val setupResult: AtomicReference<String>) : Runnable {
    private val retryMeasureCPULoad get() = Registry.intValue("intellij.appcds.install.idleRecheckCPU", 200).toLong()
    private val retryTooHighCPULoad get() = Registry.intValue("intellij.appcds.install.idleTooHeavyCPULoad", 5_000).toLong()
    private val retryPaceLong get() = Registry.intValue("intellij.appcds.install.idleLongRetry", 300_000).toLong()

    private val osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean::class.java)
    private val cpuCount = osBean.availableProcessors
    private val processCpuLoad by AverageCPULoad { osBean.processCpuLoad }
    private val systemCpuLoad by AverageCPULoad { osBean.systemCpuLoad }

    fun reschedule(time: Long) {
      AppExecutorUtil.getAppScheduledExecutorService().schedule(this, time, TimeUnit.SECONDS)
    }

    fun needsWaitingForIdleMillis(): Long? = runCatching {
      if (!Registry.`is`("intellij.appcds.install.idleTestEnabled", true)) return null

      if (PowerSaveMode.isEnabled()) return retryPaceLong
      if (PowerStatus.getPowerStatus() == PowerStatus.BATTERY) return retryPaceLong

      // check if our IDE is busy right now doing something useful
      // careful! Our CDS archive generation process would take 1 CPU
      // the supported minimum is 2 CPU!
      val processCPULoad = processCpuLoad ?: return retryMeasureCPULoad
      if (processCPULoad * cpuCount > 2.0) return retryTooHighCPULoad

      // OS CPU load, our CDS generation will consume 1 CPU anyways
      val systemCPULoad = systemCpuLoad ?: return retryMeasureCPULoad
      if (systemCPULoad * cpuCount > 3.0) return retryTooHighCPULoad

      // ensure free space under CDS folder (aka System folder)
      if (CDSPaths.freeSpaceForCDS < 3L * 1024 * 1024 * 1024) return retryPaceLong
      return null
    }.getOrNull()

    override fun run() {
      val timeToSleep = needsWaitingForIdleMillis()
      if (timeToSleep != null) return reschedule(timeToSleep)

      CDSManager.installCDS(canStillWork = {
        val wait = needsWaitingForIdleMillis()
        wait == null || wait <= retryMeasureCPULoad
      }, onResult = { result ->
        when (result) {
          is CDSTaskResult.Cancelled -> {
            if (!CDSPaths.current.hasSameEnvironmentToBuildCDSArchive()) {
              setupResult.set("enabled:classes-change-detected")
            }
            else {
              LOG.info("CDS archive generation paused due to high CPU load and will be re-scheduled later")
              reschedule(retryPaceLong)
            }
          }
          is CDSTaskResult.Failed -> {
            setupResult.set("enabled:failed")
          }
          is CDSTaskResult.Success -> {
            setupResult.set("enabled")
          }
        }
      })
    }
  }

  private class AverageCPULoad(val compute: () -> Double) {
    private val samples = 32

    private var index = 0
    private val measurements = DoubleArray(samples)
    private val times = LongArray(samples)

    private fun now() = System.currentTimeMillis()

    //helper function for Kotlin delegated property syntax
    operator fun getValue(x: Any?, p: Any): Double? = nextValue()

    /// returns null if the measured value of CPU load is not yet totally
    // defined otherwise an average value of recent measurements
    private fun nextValue(): Double? {
      val now = now()

      val nextResult = runCatching { compute() }.getOrNull() ?: return null
      if (nextResult.isNaN() || nextResult < 0.0 || nextResult > 1.0) return null

      // let's skip measurements if they happen too often
      val previousRecord = (index + samples - 1) % samples
      if (now - times[previousRecord] < 200) return null

      measurements[index] = nextResult
      times[index] = now
      index = (index + 1) % samples

      var sum = 0.0
      var count = 0
      // we consider only measurements that were collected within latest 5 minutes
      measurements.indices.forEach { idx ->
        if (now - times[idx] < TimeUnit.SECONDS.toMillis(5)) {
          sum += measurements[idx]
          count++
        }
      }

      // it is still required to have at least N measurements has to be considered
      if (count < 5) return null
      return sum / count
    }
  }
}

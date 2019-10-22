// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.cds

import com.intellij.ide.PowerSaveMode
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.io.PowerStatus
import com.sun.management.OperatingSystemMXBean
import java.lang.management.ManagementFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private val LOG = logger<CDSStartupActivity>()

class CDSStartupActivity : StartupActivity {
  private val isExecuted = AtomicBoolean(false)

  //for tests
  val setupResult = AtomicReference<String>(null)

  override fun runActivity(project: Project) {
    if (!isExecuted.compareAndSet(false, true)) return
    if (!CDSManager.isValidEnv && !Registry.`is`("intellij.appcds.assumeValidEnv", false)) return
    if (!Registry.`is`("intellij.appcds.useStartupActivity", true)) return

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
      return
    }

    CDSFUSCollector.logCDSEnabled()

    object : Runnable {
      val retryPaceReSchedule get() = Registry.intValue("intellij.appcds.install.idleRescheduleSeconds", 1).toLong()
      val retryPaceShort get() = Registry.intValue("intellij.appcds.install.idleRetrySeconds", 15).toLong()
      val retryPaceLong get() = Registry.intValue("intellij.appcds.install.idleLongRetrySeconds", 300).toLong()

      fun reschedule(time: Long) {
        AppExecutorUtil.getAppScheduledExecutorService().schedule(this, time, TimeUnit.SECONDS)
      }

      fun needsWaitingForIdleSeconds(): Long? = runCatching {
        if (!Registry.`is`("intellij.appcds.install.idleTestEnabled", true)) return null

        if (PowerSaveMode.isEnabled()) return retryPaceLong
        if (PowerStatus.getPowerStatus() == PowerStatus.BATTERY) return retryPaceLong

        val osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean::class.java)

        // our IDE or whole OS is too busy right now,
        // careful! Our CDS archive generation process would take 1 CPU
        // the supported minimum is 2 CPU!
        if (osBean.processCpuLoad > 0.6) return retryPaceShort
        if (osBean.systemCpuLoad > 0.9) return retryPaceShort

        // ensure free space under CDS folder (aka System folder)
        if (CDSPaths.freeSpaceForCDS < 3L * 1024 * 1024 * 1024) return retryPaceLong
        return null
      }.getOrNull()

      override fun run() {
        val timeToSleep = needsWaitingForIdleSeconds()
        if (timeToSleep != null) return reschedule(timeToSleep)

        CDSManager.installCDS(canStillWork = { needsWaitingForIdleSeconds() == null },
                              onResult = { result ->
                                return@installCDS when(result) {
                                  is CDSTaskResult.Cancelled -> {
                                    if (!CDSPaths.current.hasSameEnvironmentToBuildCDSArchive()) {
                                      setupResult.set("enabled:classes-change-detected")
                                    } else {
                                      LOG.info("CDS archive generation paused due to high CPU load and will be re-scheduled later")
                                      reschedule(retryPaceReSchedule)
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
    }.run()
  }
}

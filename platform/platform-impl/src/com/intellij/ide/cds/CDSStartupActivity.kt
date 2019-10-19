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
  val setupResult = AtomicReference<CDSTaskResult?>(null)

  override fun runActivity(project: Project) {
    if (!isExecuted.compareAndSet(false, true)) return
    if (!CDSManager.isValidEnv && System.getProperty("intellij.appcds.assumeValidEnv") != "true") return

    if (System.getProperty("intellij.appcds.startupActivity") == "false") return

    val isRunningWithCDS = CDSManager.isRunningWithCDS
    if (isRunningWithCDS) {
      Logger.getInstance(CDSManager::class.java).warn("Running with enabled CDS")
    }

    // 1. allow to toggle the feature via -DintelliJ.appCDS.enabled
    // 2. if not set, use Registry to enable the feature
    // 3. and finally, fallback to see if we already run with AppCDS
    val cdsEnabled = System.getProperty("intellij.appcds.enabled")?.toBoolean()
                     ?: (runCatching {
                       Registry.`is`("appcds.enabled")
                     }.getOrElse { false } || isRunningWithCDS)

    AppExecutorUtil.getAppExecutorService().submit {
      CDSManager.cleanupStaleCDSFiles(cdsEnabled)
      if (!cdsEnabled) {
        CDSManager.removeCDS()
        setupResult.set(CDSTaskResult.Success)
      }
    }

    if (!cdsEnabled) return

    object : Runnable {
      val retryPaceShort = System.getProperty("intellij.appcds.install.idleRetry")?.toLong() ?: 15
      val retryPaceLong = System.getProperty("intellij.appcds.install.idleLongRetry")?.toLong() ?: 300

      fun reschedule(time: Long) {
        AppExecutorUtil.getAppScheduledExecutorService().schedule(this, time, TimeUnit.SECONDS)
      }

      fun needsWaitingForIdleSeconds(): Long? = runCatching {
        if (System.getProperty("intellij.appcds.install.idleTest") == "false") return null

        if (PowerSaveMode.isEnabled()) return retryPaceLong
        if (PowerStatus.getPowerStatus() == PowerStatus.BATTERY) return retryPaceLong

        // our IDE is not using the whole CPU
        val osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean::class.java)
        if (osBean.processCpuLoad * osBean.availableProcessors > osBean.availableProcessors - 1.3) return retryPaceShort

        // ensure free space under CDS folder (aka System folder)
        if (CDSPaths.freeSpaceForCDS < 3L * 1024 * 1024 * 1024) return retryPaceLong
        return null
      }.getOrNull()

      override fun run() {
        val timeToSleep = needsWaitingForIdleSeconds()
        if (timeToSleep != null) return reschedule(timeToSleep)

        CDSManager.installCDS(canStillWork = { needsWaitingForIdleSeconds() == null },
                              onResult = { result ->
                                setupResult.set(result)
                                if (result is CDSTaskResult.Cancelled) {
                                  LOG.info("CDS archive generation was re-scheduled in ${StringUtil.formatDuration(retryPaceLong)}")
                                  reschedule(retryPaceLong)
                                }
                              })
      }
    }.run()
  }
}

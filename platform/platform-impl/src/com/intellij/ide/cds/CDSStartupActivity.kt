// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.cds

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.PerformInBackgroundOption
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.atomic.AtomicBoolean

class CDSStartupActivity : StartupActivity {
  private val isExecuted = AtomicBoolean(false)

  override fun runActivity(project: Project) {
    if (!isExecuted.compareAndSet(false, true)) return
    if (!CDSManager.canBuildOrUpdateCDS) return

    val isRunningWithCDS = CDSManager.isRunningWithCDS
    if (isRunningWithCDS) {
      Logger.getInstance(CDSManager::class.java).warn("Running with enabled CDS")
    }

    // 1. allow to toggle the feature via -DintelliJ.appCDS.enabled
    // 2. if not set, use Registry to enable the feature
    // 3. and finally, fallback to see if we already run with AppCDS
    val cdsKey = "appcds.enabled"
    val cdsEnabled = System.getProperty("intellij.$cdsKey")?.toBoolean()
                     ?: (runCatching {
                       Registry.`is`(cdsKey)
                     }.getOrElse { false } || isRunningWithCDS)

    AppExecutorUtil.getAppExecutorService().execute {
      ProgressManager.getInstance().run(object : Task.Backgroundable(
        null,
        "Optimizing startup performance",
        true,
        PerformInBackgroundOption.ALWAYS_BACKGROUND
      ) {
        override fun run(indicator: ProgressIndicator) {
          indicator.isIndeterminate = true

          CDSManager.cleanupStaleCDSFiles(indicator)
          if (cdsEnabled) {
            CDSManager.installCDS(indicator)
          } else {
            CDSManager.removeCDS()
          }
        }
      })
    }
  }
}

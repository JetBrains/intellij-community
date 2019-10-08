// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.cds

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

    val cdsKey = "intellij.cds.enabled"
    val cdsEnabled = (System.getProperty(cdsKey, "true")?.toBoolean() == true || Registry.`is`(cdsKey, true))

    AppExecutorUtil.getAppExecutorService().execute {
      ProgressManager.getInstance().run(object : Task.Backgroundable(
        null,
        "Class Data Sharing for faster startup",
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

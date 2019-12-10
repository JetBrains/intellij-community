// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.cds

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.PerformInBackgroundOption
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.util.concurrency.AppExecutorUtil


class CDSEnableAction : AnAction("Enable Class Data Sharing") {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = CDSManager.isValidEnv
  }

  override fun actionPerformed(e: AnActionEvent) {
    if (!CDSManager.isValidEnv) return

    AppExecutorUtil.getAppExecutorService().execute {
      ProgressManager.getInstance().run(object : Task.Backgroundable(
        null,
        "Enable Class Data Sharing (AppCDS)",
        true,
        PerformInBackgroundOption.ALWAYS_BACKGROUND
      ) {
        override fun run(indicator: ProgressIndicator) {
          indicator.isIndeterminate = true
          CDSManager.installCDS(indicator)
        }
      })
    }
  }
}

class CDSDisableAction : AnAction("Disable Class Data Sharing") {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = CDSManager.isValidEnv
  }

  override fun actionPerformed(e: AnActionEvent) {
    AppExecutorUtil.getAppExecutorService().execute {
      ProgressManager.getInstance().run(object : Task.Backgroundable(
        null,
        "Disable Class Data Sharing (AppCDS)",
        true,
        PerformInBackgroundOption.ALWAYS_BACKGROUND
      ) {
        override fun run(indicator: ProgressIndicator) {
          indicator.isIndeterminate = true
          CDSManager.removeCDS()
        }
      })
    }
  }
}

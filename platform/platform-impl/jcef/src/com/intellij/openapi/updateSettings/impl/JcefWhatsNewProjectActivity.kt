// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.actions.WhatsNewAction
import com.intellij.ide.actions.WhatsNewUtil
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.BuildNumber
import com.intellij.platform.ide.customization.ExternalProductResourceUrls
import java.util.concurrent.atomic.AtomicBoolean

internal class JcefWhatsNewProjectActivity : ProjectActivity {
  private val isStarted = AtomicBoolean(false)

  override suspend fun execute(project: Project) {
    if (isStarted.getAndSet(true)) return

    val app = ApplicationManager.getApplication()
    if (app.isCommandLine || app.isHeadlessEnvironment || app.isUnitTestMode) return

    showWhatsNew(project, ApplicationInfo.getInstance().build)
  }

  private fun showWhatsNew(project: Project, current: BuildNumber) {
    val url = ExternalProductResourceUrls.getInstance().whatIsNewPageUrl
    if (url != null &&
        WhatsNewUtil.isWhatsNewAvailable() &&
        UpdateCheckerService.shouldShowWhatsNew(current, ApplicationInfoEx.getInstanceEx().isMajorEAP)) {
      if (UpdateSettings.getInstance().isShowWhatsNewEditor) {
        ApplicationManager.getApplication().invokeLater(
          { WhatsNewAction.openWhatsNewPage(project, url.toExternalForm(), true) },
          project.disposed,
        )
        IdeUpdateUsageTriggerCollector.majorUpdateHappened(true)
      }
      else {
        IdeUpdateUsageTriggerCollector.majorUpdateHappened(false)
      }
    }
  }
}

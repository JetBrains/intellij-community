// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.actions.WhatsNewAction
import com.intellij.ide.actions.WhatsNewUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.ide.customization.ExternalProductResourceUrls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

internal class JcefWhatsNewProjectActivity : ProjectActivity {
  private val isStarted = AtomicBoolean(false)

  override suspend fun execute(project: Project) {
    if (isStarted.getAndSet(true)) return

    val app = ApplicationManager.getApplication()
    if (app.isCommandLine || app.isHeadlessEnvironment || app.isUnitTestMode) return

    val url = ExternalProductResourceUrls.getInstance().whatIsNewPageUrl
    val appInfo = ApplicationInfoEx.getInstanceEx()
    if (
      url != null &&
      WhatsNewUtil.isWhatsNewAvailable() &&
      UpdateCheckerService.shouldShowWhatsNew(appInfo.build, appInfo.isMajorEAP)
    ) {
      if (UpdateSettings.getInstance().isShowWhatsNewEditor) {
        withContext(Dispatchers.EDT) {
          WhatsNewAction.openWhatsNewPage(project, url.toExternalForm(), true)
        }
        IdeUpdateUsageTriggerCollector.majorUpdateHappened(true)
      }
      else {
        IdeUpdateUsageTriggerCollector.majorUpdateHappened(false)
      }
    }
  }
}

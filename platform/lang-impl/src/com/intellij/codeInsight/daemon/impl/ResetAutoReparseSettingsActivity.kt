// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings.AUTOREPARSE_DELAY_DEFAULT
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.ide.util.runOnceForApp
import com.intellij.lang.LangBundle
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.options.ex.ConfigurableVisitor
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

internal class ResetAutoReparseSettingsActivity : ProjectActivity {
  init {
    val app = ApplicationManager.getApplication()
    if (app.isUnitTestMode || app.isHeadlessEnvironment || app.isCommandLine) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    runOnceForApp("reset.auto.reparse.settings.to.default") {
      val didChange = backgroundWriteAction {
        val settings = DaemonCodeAnalyzerSettings.getInstance()
        if (settings.autoReparseDelay == 0) {
          settings.autoReparseDelay = AUTOREPARSE_DELAY_DEFAULT

          thisLogger().info("Setting AUTOREPARSE_DELAY default value as suspicious value 0 was stored in settings")
          return@backgroundWriteAction true
        }
        false
      }

      if (didChange) {
        val notification = NotificationGroupManager.getInstance().getNotificationGroup("System Messages")
          .createNotification(LangBundle.message("notification.content.editor.autoreparse.delay.has.been.restored", AUTOREPARSE_DELAY_DEFAULT),
                              NotificationType.INFORMATION)
        notification.setDisplayId("reset.auto.reparse.settings.to.default")
        notification.setSuppressShowingPopup(true)
        notification.addAction(NotificationAction.create(LangBundle.message("action.open.settings.text")) {
          val groups = ShowSettingsUtilImpl.getConfigurableGroups(project, withIdeSettings = true)
          val configurable = ConfigurableVisitor.findById("preferences.editor.code.editing", groups.toList())

          ShowSettingsUtil.getInstance().showSettingsDialog(project, configurable)
        })
        notification.notify(project)
      }
    }
  }
}
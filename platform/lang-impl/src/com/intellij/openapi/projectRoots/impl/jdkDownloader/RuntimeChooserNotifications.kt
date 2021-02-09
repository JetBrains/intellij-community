// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.lang.LangBundle
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.util.NlsSafe
import java.nio.file.Path

@Service(Service.Level.APP)
class RuntimeChooserNotifications {
  private val group get() = NotificationGroupManager.getInstance().getNotificationGroup("Runtime Chooser")

  fun notifyDownloadFailed(jdkItem: JdkItem,
                           @NlsSafe error: String) {
    group
      .createNotification(NotificationType.ERROR)
      .setContent(LangBundle.message("notification.content.choose.ide.runtime.download.error", jdkItem.fullPresentationText, error))
      .notify(null)
  }

  private fun Path?.toNlsStringSafe(): @NlsSafe String = runCatching {
    this?.toAbsolutePath()?.toString()
  }.getOrNull() ?: LangBundle.message("notification.content.choose.ide.runtime.no.file.part")

  fun notifySettingBootJdkFailed(jdkHome: Path, jdkFile: Path?) {
    group
      .createNotification(NotificationType.ERROR)
      .setContent(LangBundle.message("notification.content.choose.ide.runtime.set.error",
                                     jdkHome.toAbsolutePath().toString(),
                                     jdkFile.toNlsStringSafe()))
      .notify(null)
  }

  fun notifySettingDefaultBootJdkFailed(jdkFile: Path?) {
    group
      .createNotification(NotificationType.ERROR)
      .setContent(LangBundle.message("notification.content.choose.ide.runtime.set.default.error",
                                     jdkFile.toNlsStringSafe()))
      .notify(null)
  }

  fun notifyRuntimeChangeToDefaultAndRestart() {
    group
      .createNotification(NotificationType.ERROR)
      .setContent(LangBundle.message("notification.content.choose.ide.runtime.set.default"))
      .notifyWithIdeRestartAction()
  }

  fun notifyRuntimeChangeToCustomAndRestart(@NlsSafe runtimeName: String) {
    group
      .createNotification(NotificationType.ERROR)
      .setContent(LangBundle.message("notification.content.choose.ide.runtime.set.custom", runtimeName))
      .notifyWithIdeRestartAction()
  }

  private fun Notification.notifyWithIdeRestartAction() {
    val app = ApplicationManager.getApplication()
    if (app.isRestartCapable) {
      addAction(NotificationAction.createSimpleExpiring(
        LangBundle.message("notification.action.choose.ide.runtime.restart"),
        RestartIdeAction()))
    }
    else {
      addAction(NotificationAction.createSimpleExpiring(
        LangBundle.message("notification.action.choose.ide.runtime.close"),
        ExitIdeAction()))
    }

    notify(null)
  }

  private class RestartIdeAction : Runnable {
    override fun run() {
      val app = ApplicationManager.getApplication()
      if (app.isRestartCapable) {
        app.exit(false, true, true)
      }
    }
  }

  private class ExitIdeAction : Runnable {
    override fun run() {
      val app = ApplicationManager.getApplication()
      if (!app.isRestartCapable) {
        app.exit(false, true, false)
      }
    }
  }
}

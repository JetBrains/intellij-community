// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.wm.IdeFocusManager
import javax.swing.event.HyperlinkEvent


class CodeWithMe203StartupActivity : StartupActivity.DumbAware {
  companion object {
    private const val executed = "executed.1"
    const val hasEverUsedCwm = "cwm.has.been.used.setting"
  }

  override fun runActivity(project: Project) {
    return //remove  for the 203 rtm

    @Suppress("UNREACHABLE_CODE")
    if (!PropertiesComponent.getInstance().getBoolean(executed)) {
      try {
        if (!PropertiesComponent.getInstance().getBoolean(hasEverUsedCwm))
          return

        @Suppress("DialogTitleCapitalization")
        val notification = Notification("CodeWithMe", IdeBundle.message("notification.code.with.me.203.title"),
                     IdeBundle.message("notification.code.with.me.203.notice",
                                       "<a href='marketplace'>${
                                         IdeBundle.message("notification.code.with.me.203.hyperlinkText")
                                       }</a>"), NotificationType.INFORMATION, object : NotificationListener.Adapter() {
          override fun hyperlinkActivated(notification: Notification, e: HyperlinkEvent) {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, PluginManagerConfigurable::class.java) { c ->
              IdeFocusManager.getInstance(project).doWhenFocusSettlesDown {
                c.enableSearch("Code With Me")
              }
            }
          }
        })
        Notifications.Bus.notify(notification, project);
      } finally {
        PropertiesComponent.getInstance().setValue(executed, true)
      }
    }
  }
}
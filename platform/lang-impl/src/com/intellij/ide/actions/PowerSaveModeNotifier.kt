// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.IdeBundle
import com.intellij.ide.PowerSaveMode
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectPostStartupActivity
import com.intellij.openapi.util.Disposer

internal class PowerSaveModeNotifier : ProjectPostStartupActivity {
  override suspend fun execute(project: Project) {
    if (PowerSaveMode.isEnabled()) {
      notifyOnPowerSaveMode(project)
    }
  }

  companion object {
    private const val IGNORE_POWER_SAVE_MODE = "ignore.power.save.mode"

    fun notifyOnPowerSaveMode(project: Project?) {
      if (PropertiesComponent.getInstance().getBoolean(IGNORE_POWER_SAVE_MODE)) {
        return
      }

      val notification = NotificationGroupManager.getInstance().getNotificationGroup("Power Save Mode").createNotification(
        IdeBundle.message("power.save.mode.on.notification.title"),
        IdeBundle.message("power.save.mode.on.notification.content"),
        NotificationType.WARNING
      )
      notification.addAction(object : NotificationAction(IdeBundle.message("action.Anonymous.text.do.not.show.again")) {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
          PropertiesComponent.getInstance().setValue(IGNORE_POWER_SAVE_MODE, true)
          notification.expire()
        }
      })
      notification.addAction(object : NotificationAction(IdeBundle.message("power.save.mode.disable.action.title")) {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
          PowerSaveMode.setEnabled(false)
          notification.expire()
        }
      })
      notification.notify(project)
      val balloon = notification.balloon ?: return
      val bus = project?.messageBus ?: ApplicationManager.getApplication().messageBus
      val connection = bus.connect()
      Disposer.register(balloon, connection)
      connection.subscribe(PowerSaveMode.TOPIC, PowerSaveMode.Listener(notification::expire))
    }
  }
}
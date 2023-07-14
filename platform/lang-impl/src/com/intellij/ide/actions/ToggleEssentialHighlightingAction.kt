// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.EssentialHighlightingMode
import com.intellij.ide.IdeBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener

class ToggleEssentialHighlightingAction : ToggleAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun isSelected(e: AnActionEvent): Boolean {
    return EssentialHighlightingMode.isEnabled()
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    EssentialHighlightingMode.setEnabled(state)
    if (state) {
      notifyOnEssentialHighlightingMode(e.getData(CommonDataKeys.PROJECT))
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}

private const val IGNORE_ESSENTIAL_HIGHLIGHTING = "ignore.essential-highlighting.mode"

internal class EssentialHighlightingNotifier : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (EssentialHighlightingMode.isEnabled()) {
      notifyOnEssentialHighlightingMode(project)
    }
  }
}

private class NotificationExpirationListener(private val notification: Notification) : RegistryValueListener {
  override fun afterValueChanged(value: RegistryValue) {
    notification.expire()
  }
}

private fun notifyOnEssentialHighlightingMode(project: Project?) {
  if (PropertiesComponent.getInstance().getBoolean(IGNORE_ESSENTIAL_HIGHLIGHTING)) {
    return
  }

  val notification = NotificationGroupManager.getInstance().getNotificationGroup("Essential Highlighting Mode").createNotification(
    IdeBundle.message("essential-highlighting.mode.on.notification.title"),
    IdeBundle.message("essential-highlighting.mode.on.notification.content"),
    NotificationType.WARNING
  )
  notification.addAction(object : NotificationAction(IdeBundle.message("action.Anonymous.text.do.not.show.again")) {
    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
      PropertiesComponent.getInstance().setValue(IGNORE_ESSENTIAL_HIGHLIGHTING, true)
      notification.expire()
    }
  })
  notification.addAction(object : NotificationAction(IdeBundle.message("essential-highlighting.mode.disable.action.title")) {
    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
      EssentialHighlightingMode.setEnabled(false)
      notification.expire()
    }
  })
  notification.notify(project)
  val balloon = notification.balloon ?: return
  RegistryManager.getInstance().get("ide.highlighting.mode.essential").addListener(NotificationExpirationListener(notification), balloon)
}

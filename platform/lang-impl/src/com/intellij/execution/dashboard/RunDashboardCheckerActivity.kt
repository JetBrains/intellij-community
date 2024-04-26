// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.dashboard

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.RunManagerListener
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.UIBundle

private val CHECKER_EP_NAME = ExtensionPointName<RunDashboardChecker>("com.intellij.runDashboardChecker")

private const val DASHBOARD_NOTIFICATION_GROUP_ID = "Services Tool Window"
private const val DASHBOARD_MULTIPLE_RUN_CONFIGURATIONS_NOTIFICATION_ID = "run.dashboard.multiple.run.configurations"
private const val SHOW_RUN_DASHBOARD_NOTIFICATION = "show.run.dashboard.notification"
private const val RUN_CONFIGURATIONS_INCLUDED_IN_SERVICES = "run.configurations.included.in.services"


private fun getSwitchLimit(): Int = Registry.intValue("ide.services.run.configuration.switch.limit", 3)

internal class RunDashboardCheckerActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (project.isDefault || ApplicationManager.getApplication().isUnitTestMode || project.isDisposed()) {
      return
    }
    if (migrateNotificationProperty(project)) return

    val listener = RunDashboardListener(project)
    if (!listener.isEnabled()) return

    listener.checkAvailability()
    project.getMessageBus().connect().subscribe<RunManagerListener>(RunManagerListener.TOPIC, listener)
  }

  private fun migrateNotificationProperty(project: Project): Boolean {
    val isEnabled = PropertiesComponent.getInstance(project).getBoolean(SHOW_RUN_DASHBOARD_NOTIFICATION, true)
    if (isEnabled) return false

    PropertiesComponent.getInstance(project).setValue(SHOW_RUN_DASHBOARD_NOTIFICATION, true, true)
    PropertiesComponent.getInstance(project).setValue(RUN_CONFIGURATIONS_INCLUDED_IN_SERVICES, true, false)
    return true
  }

  private class RunDashboardListener(private val project: Project) : RunManagerListener {
    override fun runConfigurationAdded(settings: RunnerAndConfigurationSettings) {
      if (!isEnabled()) return

      checkAvailability()
    }

    fun isEnabled(): Boolean {
      val included = PropertiesComponent.getInstance(project).getBoolean(RUN_CONFIGURATIONS_INCLUDED_IN_SERVICES, false)
      if (included) return false

      return createNotification(emptySet(), "").canShowFor(project)
    }

    fun checkAvailability() {
      val typesToDisplay = HashSet<String>()
      val count = CHECKER_EP_NAME.extensionList.sumOf {
        val count = it.countUniqueConfigurations(project)
        if (count > 0) {
          typesToDisplay.add(it.typeId)
        }
        count
      }
      if (count < getSwitchLimit()) return

      PropertiesComponent.getInstance(project).setValue(RUN_CONFIGURATIONS_INCLUDED_IN_SERVICES, true, false)
      val types = showInServices(CHECKER_EP_NAME.extensionList.map { it.typeId })
      typesToDisplay.retainAll(types)
      if (typesToDisplay.isEmpty()) return

      val typeDisplayNames = StringUtil.join(typesToDisplay.map { getTypeDisplayName(it) }, ", ")
      createNotification(types, typeDisplayNames).notify(project)
    }

    private fun getTypeDisplayName(typeId: String): String {
      val configurationType = ConfigurationType.CONFIGURATION_TYPE_EP.extensionList.find { it.id == typeId }
      assert(configurationType != null)
      return configurationType!!.displayName
    }

    private fun showInServices(types: List<String>): Collection<String> {
      val result = HashSet<String>()
      val dashboardManager = RunDashboardManager.getInstance(project)
      val newTypes = HashSet(dashboardManager.types)
      for (type in types) {
        if (newTypes.add(type)) {
          result.add(type)
        }
      }
      dashboardManager.setTypes(newTypes)
      return result
    }

    private fun removeFromServices(types: Collection<String>) {
      val dashboardManager = RunDashboardManager.getInstance(project)
      val newTypes = HashSet(dashboardManager.types)
      newTypes.removeAll(types)
      dashboardManager.setTypes(newTypes)
    }

    private fun createNotification(types: Collection<String>, typeDisplayNames: String): Notification {
      return NotificationGroupManager.getInstance().getNotificationGroup(DASHBOARD_NOTIFICATION_GROUP_ID)
        .createNotification(ExecutionBundle.message("run.dashboard.multiple.run.config.notification", typeDisplayNames,
                                                    UIBundle.message("tool.window.name.services")),
                            NotificationType.INFORMATION)
        .setDisplayId(DASHBOARD_MULTIPLE_RUN_CONFIGURATIONS_NOTIFICATION_ID)
        .setIcon(AllIcons.Nodes.Services)
        .addAction(object : NotificationAction(ExecutionBundle.message("run.dashboard.do.not.use.services.action")) {
          override fun actionPerformed(e: AnActionEvent, notification: Notification) {
            removeFromServices(types)
            notification.expire()
          }
        })
    }
  }
}
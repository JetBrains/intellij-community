// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.ContentModuleDescriptor
import com.intellij.ide.plugins.DisabledPluginsState
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginEnabler
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginModuleId
import com.intellij.ide.plugins.PluginUtils.toPluginIdSet
import com.intellij.ide.plugins.newui.DefaultUiPluginManagerController
import com.intellij.ide.plugins.newui.MyPluginModel
import com.intellij.ide.ui.search.BooleanOptionDescription
import com.intellij.ide.ui.search.NotABooleanOptionDescription
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.updateSettings.impl.UpdateCheckerFacade
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.nio.file.FileVisitResult
import java.util.concurrent.atomic.AtomicReference

@ApiStatus.Internal
class PluginBooleanOptionDescriptor internal constructor(private val myDescriptor: IdeaPluginDescriptor) : BooleanOptionDescription(
  IdeBundle.message("search.everywhere.command.plugins", myDescriptor.name),
  PluginManagerConfigurable.ID
), BooleanOptionDescription.RequiresRebuild, NotABooleanOptionDescription {

  override fun isOptionEnabled(): Boolean {
    return !PluginEnabler.HEADLESS.isDisabled(myDescriptor.pluginId)
  }

  override fun setOptionState(enable: Boolean) {
    togglePluginState(listOf(myDescriptor), enable)
  }

  companion object {
    private val ourPreviousNotification = AtomicReference<Notification?>()

    @JvmStatic
    fun togglePluginState(descriptors: Collection<IdeaPluginDescriptor>, enable: Boolean) {
      // TODO get proper scope
      GlobalScope.launch(CoroutineName("toggle plugins state") + Dispatchers.EDT) {
        if (descriptors.isEmpty()) {
          return@launch
        }

        val pluginIdMap = PluginManagerCore.buildPluginIdMap()
        val contentModuleIdMap = PluginManagerCore.getPluginSet().buildContentModuleIdMap()
        val autoSwitchedDescriptors = if (enable) {
          getDependenciesToEnable(descriptors, pluginIdMap, contentModuleIdMap)
        }
        else {
          getDependentsToDisable(descriptors, pluginIdMap, contentModuleIdMap)
        }

        val pluginEnabler = PluginEnabler.getInstance()
        val appliedWithoutRestart = if (enable) {
          pluginEnabler.enable(autoSwitchedDescriptors)
        }
        else {
          pluginEnabler.disable(autoSwitchedDescriptors)
        }

        if (autoSwitchedDescriptors.size > descriptors.size) {
          val content = IdeBundle.message(
            if (enable) "plugins.auto.enabled.notification.content" else "plugins.auto.disabled.notification.content",
            MyPluginModel.joinPluginNamesOrIds(MyPluginModel.getPluginNames(descriptors)),
            MyPluginModel.joinPluginNamesOrIds(MyPluginModel.getPluginNames(autoSwitchedDescriptors))
          )
          showAutoSwitchNotification(autoSwitchedDescriptors, pluginEnabler, content, enable)
        }

        notifyIfRestartRequired(!appliedWithoutRestart)
      }
    }

    private fun showAutoSwitchNotification(
      descriptors: Collection<IdeaPluginDescriptor>,
      pluginEnabler: PluginEnabler,
      content: @Nls String,
      enabled: Boolean
    ) {
      val title = IdeBundle.message(if (enabled) "plugins.auto.enabled.notification.title" else "plugins.auto.disabled.notification.title")
      val switchNotification = UpdateCheckerFacade.getInstance().getNotificationGroupForPluginUpdateResults()
        .createNotification(content, NotificationType.INFORMATION)
        .setDisplayId("plugin.auto.switch")
        .setTitle(title)
        .addAction(object : NotificationAction(IdeBundle.message("plugins.auto.switch.action.name")) {
          override fun actionPerformed(e: AnActionEvent, notification: Notification) {
            val appliedWithoutRestart = if (enabled) {
              pluginEnabler.disable(descriptors)
            }
            else {
              pluginEnabler.enable(descriptors)
            }
            notification.expire()
            notifyIfRestartRequired(!appliedWithoutRestart)
          }
        })

      val pluginIds = descriptors.toPluginIdSet()
      DisabledPluginsState.addDisablePluginListener(object : Runnable {
        override fun run() {
          val condition: (PluginId) -> Boolean = { pluginEnabler.isDisabled(it) }
          val notificationValid = if (enabled) {
            !pluginIds.any(condition)
          }
          else {
            pluginIds.all(condition)
          }
          if (!notificationValid) {
            switchNotification.expire()
          }

          val balloon = switchNotification.balloon
          if (balloon == null || balloon.isDisposed) {
            ApplicationManager.getApplication().invokeLater {
              DisabledPluginsState.removeDisablePluginListener(this)
            }
          }
        }
      })
      switchNotification.notify(null)
    }

    private fun getDependenciesToEnable(
      descriptors: Collection<IdeaPluginDescriptor>,
      pluginIdMap: Map<PluginId, IdeaPluginDescriptorImpl>,
      contentModuleIdMap: Map<PluginModuleId, ContentModuleDescriptor>
    ): Collection<IdeaPluginDescriptor> {
      val result = LinkedHashSet<IdeaPluginDescriptor>()

      for (descriptor in descriptors) {
        result.add(descriptor)
        if (descriptor !is IdeaPluginDescriptorImpl) {
          continue
        }

        PluginManagerCore.processAllNonOptionalDependencies(descriptor, pluginIdMap, contentModuleIdMap) { dependency ->
          if (PluginManagerCore.CORE_ID == dependency.pluginId ||
              (PluginManagerCore.ULTIMATE_PLUGIN_ID == dependency.pluginId &&
               PluginManagerCore.isDisabled(PluginManagerCore.ULTIMATE_PLUGIN_ID)) ||
              dependency.isEnabled ||
              !result.add(dependency)) {
            FileVisitResult.SKIP_SUBTREE /* if descriptor has already been added/enabled, no need to process its dependencies */
          }
          else {
            FileVisitResult.CONTINUE
          }
        }
      }

      return java.util.Collections.unmodifiableSet(result)
    }

    private fun getDependentsToDisable(
      descriptors: Collection<IdeaPluginDescriptor>,
      pluginIdMap: Map<PluginId, IdeaPluginDescriptorImpl>,
      contentModuleIdMap: Map<PluginModuleId, ContentModuleDescriptor>
    ): Collection<IdeaPluginDescriptor> {
      val result = LinkedHashSet<IdeaPluginDescriptor>()
      val applicationInfo = ApplicationInfoEx.getInstanceEx()

      for (descriptor in descriptors) {
        result.add(descriptor)
        result.addAll(DefaultUiPluginManagerController.getDependents(descriptor.pluginId, applicationInfo, pluginIdMap, contentModuleIdMap))
      }

      return java.util.Collections.unmodifiableSet(result)
    }

    private fun notifyIfRestartRequired(restartRequired: Boolean) {
      if (!restartRequired) {
        return
      }

      val notification = ourPreviousNotification.get()
      if (notification != null) {
        val balloon = notification.balloon
        if (balloon != null && !balloon.isDisposed) {
          return
        }
      }

      val newNotification = UpdateCheckerFacade.getInstance().getNotificationGroupForIdeUpdateResults()
        .createNotification(
          IdeBundle.message("plugins.changed.notification.content", ApplicationNamesInfo.getInstance().fullProductName),
          NotificationType.INFORMATION
        )
        .setTitle(IdeBundle.message("plugins.changed.notification.title"))
        .setDisplayId("plugins.updated.restart.required")
        .addAction(object : DumbAwareAction(IdeBundle.message("ide.restart.action")) {
          override fun actionPerformed(e: AnActionEvent) {
            ApplicationManager.getApplication().restart()
          }
        })

      if (ourPreviousNotification.compareAndSet(notification, newNotification)) {
        newNotification.notify(null)
      }
    }
  }
}

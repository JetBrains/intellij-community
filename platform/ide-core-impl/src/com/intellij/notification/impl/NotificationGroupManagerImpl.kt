// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification.impl

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.util.concurrency.SynchronizedClearableLazy
import java.util.concurrent.ConcurrentHashMap

private val LOG = logger<NotificationGroupManagerImpl>()

class NotificationGroupManagerImpl private constructor() : NotificationGroupManager {
  private val registeredGroups = computeGroups()

  private val registeredNotificationIds = SynchronizedClearableLazy<Set<String>> {
    val result = HashSet<String>()
    NotificationGroupEP.EP_NAME.processWithPluginDescriptor { extension, pluginDescriptor ->
      if (extension.notificationIds != null && PluginManagerCore.isDevelopedByJetBrains(pluginDescriptor)) {
        result.addAll(extension.notificationIds!!)
      }
    }
    result
  }

  init {
    NotificationGroupEP.EP_NAME.addExtensionPointListener(object : ExtensionPointListener<NotificationGroupEP> {
      override fun extensionAdded(extension: NotificationGroupEP, pluginDescriptor: PluginDescriptor) {
        registerNotificationGroup(extension, pluginDescriptor, registeredGroups)
        if (extension.notificationIds != null) {
          registeredNotificationIds.drop()
        }
      }

      override fun extensionRemoved(extension: NotificationGroupEP, pluginDescriptor: PluginDescriptor) {
        val group = registeredGroups[extension.id]!!
        if (group.pluginId == pluginDescriptor.pluginId) {
          registeredGroups.remove(extension.id)
        }
        if (extension.notificationIds != null) {
          registeredNotificationIds.drop()
        }
      }
    }, null)
  }

  override fun getNotificationGroup(groupId: String): NotificationGroup? = registeredGroups[groupId]

  override fun isGroupRegistered(groupId: String) = registeredGroups.containsKey(groupId)

  override fun getRegisteredNotificationGroups() = registeredGroups.values

  override fun isRegisteredNotificationId(notificationId: String) = registeredNotificationIds.value.contains(notificationId)
}

private fun computeGroups(): MutableMap<String, NotificationGroup> {
  val result = HashMap<String, NotificationGroup>(NotificationGroupEP.EP_NAME.point.size())
  NotificationGroupEP.EP_NAME.processWithPluginDescriptor { extension, descriptor ->
    registerNotificationGroup(extension, descriptor, result)
  }
  return ConcurrentHashMap(result)
}

private fun registerNotificationGroup(extension: NotificationGroupEP,
                                      pluginDescriptor: PluginDescriptor,
                                      registeredGroups: MutableMap<String, NotificationGroup>) {
  try {
    val groupId = extension.id
    val type = extension.getDisplayType()
    if (type == null) {
      LOG.warn("Cannot create notification group \"$groupId`\": displayType should be not null")
      return
    }

    val notificationGroup = NotificationGroup.create(
      displayId = groupId,
      displayType = type,
      isLogByDefault = extension.isLogByDefault,
      toolWindowId = extension.toolWindowId,
      title = extension.getDisplayName(pluginDescriptor),
      pluginId = pluginDescriptor.pluginId)
    notificationGroup.isHideFromSettings = extension.hideFromSettings
    registeredGroups.put(groupId, notificationGroup)?.let { old ->
      LOG.warn("Notification group $groupId is already registered (group=$old). Plugin descriptor: $pluginDescriptor")
    }
  }
  catch (e: Exception) {
    LOG.warn("Cannot create notification group: $extension", e)
  }
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification

import com.intellij.ide.plugins.PluginUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.util.NlsContexts.*
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Icon

/**
 * Groups notifications and allows controlling display options in Settings.
 */
class NotificationGroup private constructor(@param:NonNls val displayId: String,
                                            val displayType: NotificationDisplayType,
                                            val isLogByDefault: Boolean = true,
                                            @param:NonNls val toolWindowId: String? = null,
                                            val icon: Icon? = null,
                                            @NotificationTitle var title: String? = null,
                                            pluginId: PluginId? = null,
                                            registerGroup: Boolean = false) {
  @Deprecated("Use com.intellij.notification.impl.NotificationGroupEP and com.intellij.notification.NotificationGroupManager")
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  constructor(@NonNls displayId: String,
              displayType: NotificationDisplayType,
              isLogByDefault: Boolean = true,
              @NonNls toolWindowId: String? = null,
              icon: Icon? = null,
              @NotificationTitle title: String? = null,
              pluginId: PluginId? = null) : this(displayId = displayId, displayType = displayType, isLogByDefault = isLogByDefault,
                                                 toolWindowId = toolWindowId, icon = icon, title = title, pluginId = pluginId,
                                                 registerGroup = true)

  // Don't use @JvmOverloads for primary constructor to maintain binary API compatibility with plugins written in Kotlin
  @JvmOverloads
  @Deprecated("Use com.intellij.notification.impl.NotificationGroupEP and com.intellij.notification.NotificationGroupManager")
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  constructor(@NonNls displayId: String,
              displayType: NotificationDisplayType,
              isLogByDefault: Boolean = true,
              @NonNls toolWindowId: String? = null,
              icon: Icon? = null) : this(displayId = displayId, displayType = displayType, isLogByDefault = isLogByDefault,
                                         toolWindowId = toolWindowId, icon = icon, registerGroup = true)

  var parentId: String? = null
    private set

  val pluginId = ApplicationManager.getApplication()?.let {
    pluginId ?: PluginUtil.getInstance().findPluginId(Throwable())
  }

  init {
    if (registerGroup) {
      if (registeredGroups.containsKey(displayId)) {
        LOG.info("Notification group $displayId is already registered", Throwable())
      }
      registeredGroups.put(displayId, this)

      if (title == null) {
        @Suppress("HardCodedStringLiteral")
        title = registeredTitles[displayId]
      }
    }
  }

  companion object {
    private val LOG = logger<NotificationGroup>()

    private val registeredGroups: MutableMap<String, NotificationGroup> = ConcurrentHashMap()
    private val registeredTitles: MutableMap<@NonNls String, @NotificationTitle String> = ConcurrentHashMap()

    @JvmStatic
    fun create(@NonNls displayId: String,
               displayType: NotificationDisplayType,
               isLogByDefault: Boolean,
               @NonNls toolWindowId: String?,
               icon: Icon?,
               @NotificationTitle title: String?,
               pluginId: PluginId?): NotificationGroup {
      return NotificationGroup(displayId, displayType, isLogByDefault, toolWindowId, icon, title, pluginId, false)
    }

    @JvmStatic
    @Deprecated("Use com.intellij.notification.impl.NotificationGroupEP and com.intellij.notification.NotificationGroupManager")
    fun balloonGroup(@NonNls displayId: String): NotificationGroup {
      val notificationGroup = findRegisteredNotificationGroup(displayId)
      if (notificationGroup != null) {
        return notificationGroup
      }
      return NotificationGroup(displayId, NotificationDisplayType.BALLOON)
    }

    @JvmStatic
    @Deprecated("Use com.intellij.notification.impl.NotificationGroupEP and com.intellij.notification.NotificationGroupManager")
    @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
    fun balloonGroup(@NonNls displayId: String, @NotificationTitle title: String?): NotificationGroup {
      val notificationGroup = findRegisteredNotificationGroup(displayId)
      if (notificationGroup != null) {
        return notificationGroup
      }
      return NotificationGroup(displayId, NotificationDisplayType.BALLOON, title = title)
    }

    @JvmStatic
    @Deprecated("Use com.intellij.notification.impl.NotificationGroupEP and com.intellij.notification.NotificationGroupManager")
    fun balloonGroup(@NonNls displayId: String, pluginId: PluginId): NotificationGroup {
      val notificationGroup = findRegisteredNotificationGroup(displayId)
      if (notificationGroup != null) {
        return notificationGroup
      }
      return NotificationGroup(displayId, NotificationDisplayType.BALLOON, pluginId = pluginId)
    }

    @JvmStatic
    @Deprecated("Use com.intellij.notification.impl.NotificationGroupEP and com.intellij.notification.NotificationGroupManager")
    fun logOnlyGroup(@NonNls displayId: String): NotificationGroup {
      val notificationGroup = findRegisteredNotificationGroup(displayId)
      if (notificationGroup != null) {
        return notificationGroup
      }
      return NotificationGroup(displayId, NotificationDisplayType.NONE)
    }

    @JvmStatic
    @Deprecated("Use com.intellij.notification.impl.NotificationGroupEP and com.intellij.notification.NotificationGroupManager")
    fun logOnlyGroup(@NonNls displayId: String, @NotificationTitle title: String?): NotificationGroup {
      val notificationGroup = findRegisteredNotificationGroup(displayId)
      if (notificationGroup != null) {
        return notificationGroup
      }
      return NotificationGroup(displayId, NotificationDisplayType.NONE, title = title)
    }

    @JvmStatic
    @Deprecated("Use com.intellij.notification.impl.NotificationGroupEP and com.intellij.notification.NotificationGroupManager")
    fun logOnlyGroup(@NonNls displayId: String, pluginId: PluginId): NotificationGroup {
      val notificationGroup = findRegisteredNotificationGroup(displayId)
      if (notificationGroup != null) {
        return notificationGroup
      }
      return NotificationGroup(displayId, NotificationDisplayType.NONE, pluginId = pluginId)
    }

    @JvmOverloads
    @JvmStatic
    @Deprecated("Use com.intellij.notification.impl.NotificationGroupEP and com.intellij.notification.NotificationGroupManager")
    fun toolWindowGroup(@NonNls displayId: String, @NonNls toolWindowId: String, logByDefault: Boolean = true): NotificationGroup {
      val notificationGroup = findRegisteredNotificationGroup(displayId)
      if (notificationGroup != null) {
        return notificationGroup
      }
      return NotificationGroup(displayId, NotificationDisplayType.TOOL_WINDOW, logByDefault, toolWindowId)
    }

    @JvmOverloads
    @JvmStatic
    @Deprecated("Use com.intellij.notification.impl.NotificationGroupEP and com.intellij.notification.NotificationGroupManager")
    fun toolWindowGroup(@NonNls displayId: String,
                        @NonNls toolWindowId: String,
                        logByDefault: Boolean = true,
                        @NotificationTitle title: String?): NotificationGroup {
      val notificationGroup = findRegisteredNotificationGroup(displayId)
      if (notificationGroup != null) {
        return notificationGroup
      }
      return NotificationGroup(displayId, NotificationDisplayType.TOOL_WINDOW, logByDefault, toolWindowId, title = title)
    }

    @JvmStatic
    @Deprecated("Use com.intellij.notification.impl.NotificationGroupEP and com.intellij.notification.NotificationGroupManager")
    fun toolWindowGroup(@NonNls displayId: String, @NonNls toolWindowId: String, logByDefault: Boolean, pluginId: PluginId): NotificationGroup {
      val notificationGroup = findRegisteredNotificationGroup(displayId)
      if (notificationGroup != null) {
        return notificationGroup
      }
      return NotificationGroup(displayId, NotificationDisplayType.TOOL_WINDOW, logByDefault, toolWindowId, pluginId = pluginId)
    }

    @JvmStatic
    fun findRegisteredGroup(@NonNls displayId: String): NotificationGroup? {
      var notificationGroup = findRegisteredNotificationGroup(displayId)
      if (notificationGroup == null) {
        notificationGroup = registeredGroups.get(displayId)
      }
      return notificationGroup
    }

    private fun findRegisteredNotificationGroup(displayId: String): NotificationGroup? {
      if (ApplicationManager.getApplication() == null) return null
      return NotificationGroupManager.getInstance().getNotificationGroup(displayId)
    }

    @JvmStatic
    fun getGroupTitle(@NonNls displayId: String): String? {
      val group = findRegisteredGroup(displayId)
      if (group?.title != null) {
        return group.title
      }
      return registeredTitles[displayId]
    }

    @JvmStatic
    fun createIdWithTitle(@NonNls displayId: String, @NotificationTitle title: String): String {
      val oldTitle = registeredTitles.put(displayId, title)
      LOG.assertTrue(oldTitle == null || oldTitle == title, "NotificationGroup('$displayId', '$oldTitle') tried to be re-created with different title '$title'")
      return displayId
    }

    @JvmStatic
    val allRegisteredGroups: Iterable<NotificationGroup>
      get() = ContainerUtil.concat(NotificationGroupManager.getInstance().registeredNotificationGroups, registeredGroups.values)
  }

  fun createNotification(@NotificationContent content: String, type: MessageType): Notification {
    return createNotification(content, type.toNotificationType())
  }

  fun createNotification(@NotificationContent content: String, type: NotificationType): Notification {
    return createNotification("", content, type)
  }

  fun createNotification(@NotificationContent content: String, type: NotificationType, displayId: String): Notification {
    return createNotification("", content, type, null, displayId)
  }

  fun createNotification(
    @NotificationTitle title: String,
    @NotificationContent content: String,
    type: NotificationType = NotificationType.INFORMATION,
    listener: NotificationListener? = null,
    notificationDisplayId: String? = null
  ): Notification {
    return Notification(displayId, notificationDisplayId, title, content, type, listener)
  }

  fun createNotification(
    @NotificationTitle title: String,
    @NotificationContent content: String,
    type: NotificationType = NotificationType.INFORMATION,
    listener: NotificationListener? = null
  ): Notification {
    return createNotification(title, content, type, listener, null)
  }

  @JvmOverloads
  fun createNotification(type: NotificationType = NotificationType.INFORMATION): Notification {
    return createNotification(title = null, subtitle = null, content = null, type = type)
  }

  @JvmOverloads
  fun createNotification(
    @NotificationTitle title: String?,
    @NotificationSubtitle subtitle: String?,
    @NotificationContent content: String?,
    type: NotificationType = NotificationType.INFORMATION,
    listener: NotificationListener? = null
  ): Notification {
    return Notification(displayId, icon, title, subtitle, content, type, listener)
  }

  fun setParentId(value: String): NotificationGroup {
    parentId = value
    return this
  }
}
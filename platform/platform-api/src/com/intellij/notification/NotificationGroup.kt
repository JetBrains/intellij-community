// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification

import com.intellij.ide.plugins.PluginUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.util.NlsContexts.*
import org.jetbrains.annotations.NonNls
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Icon

private val LOG = logger<NotificationGroup>()
private val registeredGroups: MutableMap<String, NotificationGroup> = ConcurrentHashMap()
private val registeredTitles: MutableMap<String, String> = ConcurrentHashMap()

/**
 * Groups notifications and allows controlling display options in Settings.
 */
class NotificationGroup(@param:NonNls val displayId: String,
                        val displayType: NotificationDisplayType,
                        val isLogByDefault: Boolean = true,
                        @param:NonNls val toolWindowId: String? = null,
                        val icon: Icon? = null,
                        var title: @NotificationTitle String? = null,
                        pluginId: PluginId? = null) {

  // Don't use @JvmOverloads for primary constructor to maintain binary API compatibility with plugins written in Kotlin
  @JvmOverloads
  constructor(@NonNls displayId: String,
              displayType: NotificationDisplayType,
              isLogByDefault: Boolean = true,
              @NonNls toolWindowId: String? = null,
              icon: Icon? = null) : this(displayId, displayType, isLogByDefault, toolWindowId, icon, null)

  var parentId: String? = null
    private set

  val pluginId = ApplicationManager.getApplication()?.let {
    pluginId ?: PluginUtil.getInstance().findPluginId(Throwable())
  }

  init {
    if (registeredGroups.containsKey(displayId)) {
      LOG.info("Notification group $displayId is already registered", Throwable())
    }
    registeredGroups.put(displayId, this)

    if (title == null) {
      title = registeredTitles[displayId]
    }
  }

  companion object {
    @JvmStatic
    fun balloonGroup(@NonNls displayId: String): NotificationGroup {
      return NotificationGroup(displayId, NotificationDisplayType.BALLOON)
    }

    @JvmStatic
    fun balloonGroup(@NonNls displayId: String, title: @NotificationTitle String?): NotificationGroup {
      return NotificationGroup(displayId, NotificationDisplayType.BALLOON, title = title)
    }

    @JvmStatic
    fun balloonGroup(@NonNls displayId: String, pluginId: PluginId): NotificationGroup {
      return NotificationGroup(displayId, NotificationDisplayType.BALLOON, pluginId = pluginId)
    }

    @JvmStatic
    fun logOnlyGroup(@NonNls displayId: String): NotificationGroup {
      return NotificationGroup(displayId, NotificationDisplayType.NONE)
    }

    @JvmStatic
    fun logOnlyGroup(@NonNls displayId: String, title: @NotificationTitle String?): NotificationGroup {
      return NotificationGroup(displayId, NotificationDisplayType.NONE, title = title)
    }

    @JvmStatic
    fun logOnlyGroup(@NonNls displayId: String, pluginId: PluginId): NotificationGroup {
      return NotificationGroup(displayId, NotificationDisplayType.NONE, pluginId = pluginId)
    }

    @JvmOverloads
    @JvmStatic
    fun toolWindowGroup(@NonNls displayId: String, @NonNls toolWindowId: String, logByDefault: Boolean = true): NotificationGroup {
      return NotificationGroup(displayId, NotificationDisplayType.TOOL_WINDOW, logByDefault, toolWindowId)
    }

    @JvmOverloads
    @JvmStatic
    fun toolWindowGroup(@NonNls displayId: String,
                        @NonNls toolWindowId: String,
                        logByDefault: Boolean = true,
                        title: @NotificationTitle String?): NotificationGroup {
      return NotificationGroup(displayId, NotificationDisplayType.TOOL_WINDOW, logByDefault, toolWindowId, title = title)
    }

    @JvmStatic
    fun toolWindowGroup(@NonNls displayId: String, @NonNls toolWindowId: String, logByDefault: Boolean, pluginId: PluginId): NotificationGroup {
      return NotificationGroup(displayId, NotificationDisplayType.TOOL_WINDOW, logByDefault, toolWindowId, pluginId = pluginId)
    }

    @JvmStatic
    fun findRegisteredGroup(displayId: String): NotificationGroup? {
      return registeredGroups.get(displayId)
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
    fun createIdWithTitle(@NonNls displayId: String, title: @NotificationTitle String): String {
      val oldTitle = registeredTitles.put(displayId, title)
      LOG.assertTrue(oldTitle == null || oldTitle == title, "New title \"$title\" for NotificationGroup($displayId,$oldTitle)")
      return displayId
    }

    @JvmStatic
    val allRegisteredGroups: Iterable<NotificationGroup>
      get() = registeredGroups.values
  }

  fun createNotification(content: @NotificationContent String, type: MessageType): Notification {
    return createNotification(content, type.toNotificationType())
  }

  fun createNotification(content: @NotificationContent String, type: NotificationType): Notification {
    return createNotification("", content, type)
  }

  fun createNotification(
    title: @NotificationTitle String,
    content: @NotificationContent String,
    type: NotificationType = NotificationType.INFORMATION,
    listener: NotificationListener? = null,
    notificationDisplayId: String? = null
  ): Notification {
    return Notification(displayId, notificationDisplayId, title, content, type, listener)
  }

  fun createNotification(
    title: @NotificationTitle String,
    content: @NotificationContent String,
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
    title: @NotificationTitle String?,
    subtitle: @NotificationSubtitle String?,
    content: @NotificationContent String?,
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
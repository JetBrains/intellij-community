// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.notification

import com.intellij.ide.plugins.PluginUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.util.NlsContexts.*
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Icon

/**
 * Groups notifications and allows controlling display options in Settings.
 *
 * The class is no longer as important in client code as it used to be: nowadays, notification groups have to be registered in XML,
 * so a group ID is enough to show a [notification][Notification].
 */
class NotificationGroup private constructor(val displayId: String,
                                            val displayType: NotificationDisplayType,
                                            val isLogByDefault: Boolean = true,
                                            val toolWindowId: String? = null,
                                            @NotificationTitle private var title: String? = null,
                                            pluginId: PluginId? = null,
                                            registerGroup: Boolean = false) {
  //<editor-fold desc="Deprecated stuff.">
  @Deprecated("Not used in the UI. For notifications, please use `Notification#setIcon` instead.")
  @ApiStatus.ScheduledForRemoval
  val icon: Icon? = null

  @Deprecated("Use `com.intellij.notification.impl.NotificationGroupEP` and `com.intellij.notification.NotificationGroupManager`")
  @ApiStatus.ScheduledForRemoval
  constructor(displayId: String,
              displayType: NotificationDisplayType,
              isLogByDefault: Boolean = true,
              toolWindowId: String? = null,
              @Suppress("UNUSED_PARAMETER") icon: Icon? = null,
              @NotificationTitle title: String? = null,
              pluginId: PluginId? = null) :
    this(displayId, displayType, isLogByDefault, toolWindowId, title, pluginId, true)

  @JvmOverloads
  @Deprecated("Use `com.intellij.notification.impl.NotificationGroupEP` and `com.intellij.notification.NotificationGroupManager`")
  @ApiStatus.ScheduledForRemoval
  constructor(displayId: String,
              displayType: NotificationDisplayType,
              isLogByDefault: Boolean = true,
              toolWindowId: String? = null,
              @Suppress("UNUSED_PARAMETER") icon: Icon? = null) :
    this(displayId, displayType, isLogByDefault, toolWindowId, registerGroup = true)
  //</editor-fold>

  var parentId: String? = null
    private set

  var isHideFromSettings: Boolean = false

  val pluginId = pluginId ?: ApplicationManager.getApplication()?.let { PluginUtil.getInstance().findPluginId(Throwable()) }

  init {
    if (registerGroup) {
      if (registeredGroups.containsKey(displayId)) {
        logger<NotificationGroup>().info("Notification group ${displayId} is already registered", Throwable())
      }
      registeredGroups[displayId] = this

      if (title == null) {
        @Suppress("HardCodedStringLiteral")
        title = registeredTitles[displayId]
      }
    }
  }

  companion object {
    private val registeredGroups = ConcurrentHashMap<String, NotificationGroup>()
    private val registeredTitles = ConcurrentHashMap<String, @NotificationTitle String>()

    @ApiStatus.Internal
    fun create(displayId: String,
               displayType: NotificationDisplayType,
               isLogByDefault: Boolean,
               toolWindowId: String?,
               @NotificationTitle title: String?,
               pluginId: PluginId?): NotificationGroup =
      NotificationGroup(displayId, displayType, isLogByDefault, toolWindowId, title, pluginId, false)

    //<editor-fold desc="Deprecated stuff.">
    @JvmStatic
    @Deprecated("Use com.intellij.notification.impl.NotificationGroupEP and com.intellij.notification.NotificationGroupManager")
    @Suppress("DEPRECATION")
    fun balloonGroup(displayId: String): NotificationGroup {
      val notificationGroup = findRegisteredNotificationGroup(displayId)
      if (notificationGroup != null) {
        return notificationGroup
      }
      return NotificationGroup(displayId, NotificationDisplayType.BALLOON)
    }

    @JvmStatic
    @Deprecated("Use com.intellij.notification.impl.NotificationGroupEP and com.intellij.notification.NotificationGroupManager")
    @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
    @Suppress("DEPRECATION")
    fun balloonGroup(displayId: String, @NotificationTitle title: String?): NotificationGroup {
      val notificationGroup = findRegisteredNotificationGroup(displayId)
      if (notificationGroup != null) {
        return notificationGroup
      }
      return NotificationGroup(displayId = displayId, displayType = NotificationDisplayType.BALLOON, title = title, registerGroup = true)
    }

    @JvmStatic
    @Deprecated("Use com.intellij.notification.impl.NotificationGroupEP and com.intellij.notification.NotificationGroupManager")
    @Suppress("DEPRECATION")
    fun balloonGroup(displayId: String, pluginId: PluginId): NotificationGroup {
      val notificationGroup = findRegisteredNotificationGroup(displayId)
      if (notificationGroup != null) {
        return notificationGroup
      }
      return NotificationGroup(displayId, NotificationDisplayType.BALLOON, pluginId = pluginId, registerGroup = true)
    }

    @JvmStatic
    @Deprecated("Use com.intellij.notification.impl.NotificationGroupEP and com.intellij.notification.NotificationGroupManager")
    @Suppress("DEPRECATION")
    fun logOnlyGroup(displayId: String): NotificationGroup {
      val notificationGroup = findRegisteredNotificationGroup(displayId)
      if (notificationGroup != null) {
        return notificationGroup
      }
      return NotificationGroup(displayId, NotificationDisplayType.NONE)
    }

    @JvmStatic
    @Deprecated("Use com.intellij.notification.impl.NotificationGroupEP and com.intellij.notification.NotificationGroupManager")
    @Suppress("DEPRECATION")
    fun logOnlyGroup(displayId: String, @NotificationTitle title: String?): NotificationGroup {
      val notificationGroup = findRegisteredNotificationGroup(displayId)
      if (notificationGroup != null) {
        return notificationGroup
      }
      return NotificationGroup(displayId, NotificationDisplayType.NONE, title = title, registerGroup = true)
    }

    @JvmStatic
    @Deprecated("Use com.intellij.notification.impl.NotificationGroupEP and com.intellij.notification.NotificationGroupManager")
    @Suppress("DEPRECATION")
    fun logOnlyGroup(displayId: String, pluginId: PluginId): NotificationGroup {
      val notificationGroup = findRegisteredNotificationGroup(displayId)
      if (notificationGroup != null) {
        return notificationGroup
      }
      return NotificationGroup(displayId, NotificationDisplayType.NONE, pluginId = pluginId, registerGroup = true)
    }

    @JvmOverloads
    @JvmStatic
    @Deprecated("Use com.intellij.notification.impl.NotificationGroupEP and com.intellij.notification.NotificationGroupManager")
    @Suppress("DEPRECATION")
    fun toolWindowGroup(displayId: String, toolWindowId: String, logByDefault: Boolean = true): NotificationGroup {
      val notificationGroup = findRegisteredNotificationGroup(displayId)
      if (notificationGroup != null) {
        return notificationGroup
      }
      return NotificationGroup(displayId, NotificationDisplayType.TOOL_WINDOW, logByDefault, toolWindowId)
    }

    @JvmStatic
    @Deprecated("Use com.intellij.notification.impl.NotificationGroupEP and com.intellij.notification.NotificationGroupManager")
    @Suppress("DEPRECATION")
    fun toolWindowGroup(displayId: String, toolWindowId: String, logByDefault: Boolean, pluginId: PluginId): NotificationGroup {
      val notificationGroup = findRegisteredNotificationGroup(displayId)
      if (notificationGroup != null) {
        return notificationGroup
      }
      return NotificationGroup(displayId, NotificationDisplayType.TOOL_WINDOW, logByDefault, toolWindowId, pluginId = pluginId, registerGroup = true)
    }
    //</editor-fold>

    @JvmStatic
    fun findRegisteredGroup(displayId: String): NotificationGroup? {
      return findRegisteredNotificationGroup(displayId) ?: registeredGroups.get(displayId)
    }

    fun isGroupRegistered(displayId: String): Boolean {
      return registeredGroups.containsKey(displayId) || NotificationGroupManager.getInstance().isGroupRegistered(displayId)
    }

    private fun findRegisteredNotificationGroup(displayId: String): NotificationGroup? {
      return if (ApplicationManager.getApplication() == null) {
        null
      }
      else {
        NotificationGroupManager.getInstance().getNotificationGroup(displayId)
      }
    }

    @JvmStatic
    fun getGroupTitle(displayId: String): String? = findRegisteredGroup(displayId)?.title ?: registeredTitles[displayId]

    @JvmStatic
    @Deprecated("Use `<notificationGroup>` extension point to register notification groups")
    fun createIdWithTitle(displayId: String, @NotificationTitle title: String): String {
      val oldTitle = registeredTitles.put(displayId, title)
      if (oldTitle != null && oldTitle != title) {
        logger<NotificationGroup>().error("NotificationGroup('${displayId}', '${oldTitle}') was re-created with different title '${title}'")
      }
      return displayId
    }

    val allRegisteredGroups: Iterable<NotificationGroup>
      get() = NotificationGroupManager.getInstance().registeredNotificationGroups.asSequence().plus(registeredGroups.values).asIterable()
  }

  fun createNotification(@NotificationContent content: String, type: MessageType): Notification {
    return createNotification(content = content, type = type.toNotificationType())
  }

  fun createNotification(@NotificationContent content: String, type: NotificationType): Notification = createNotification("", content, type)

  fun createNotification(@NotificationTitle title: String, @NotificationContent content: String, type: NotificationType): Notification {
    return Notification(displayId, title, content, type)
  }

  //<editor-fold desc="Deprecated stuff.">
  @Deprecated("Use `createNotification(String, String, NotificationType)` along with `Notification#setListener`")
  @Suppress("DeprecatedCallableAddReplaceWith")
  fun createNotification(@NotificationTitle title: String,
                         @NotificationContent content: String,
                         type: NotificationType = NotificationType.INFORMATION,
                         listener: NotificationListener? = null): Notification {
    val result = createNotification(title, content, type)
    if (listener != null) {
      @Suppress("DEPRECATION")
      result.setListener(listener)
    }
    return result
  }

  @Deprecated("Use `createNotification(String, String, NotificationType)` along with `Notification#setDisplayId` and `Notification#setListener`")
  @Suppress("DeprecatedCallableAddReplaceWith")
  fun createNotification(@NotificationTitle title: String,
                         @NotificationContent content: String,
                         type: NotificationType = NotificationType.INFORMATION,
                         listener: NotificationListener? = null,
                         notificationDisplayId: String? = null): Notification {
    val result = createNotification(title, content, type)
    if (notificationDisplayId != null) {
      result.setDisplayId(notificationDisplayId)
    }
    if (listener != null) {
      @Suppress("DEPRECATION")
      result.setListener(listener)
    }
    return result
  }

  @Deprecated("Use `createNotification(String, NotificationType)` or `createNotification(String, String, NotificationType)`")
  @Suppress("DeprecatedCallableAddReplaceWith")
  @JvmOverloads
  fun createNotification(type: NotificationType = NotificationType.INFORMATION): Notification = Notification(displayId, "", type)

  @Deprecated("Use `createNotification(String, NotificationType)` or `createNotification(String, String, NotificationType)`" +
              " along with `Notification#setSubtitle` and `Notification#setListener`")
  @Suppress("DeprecatedCallableAddReplaceWith")
  @JvmOverloads
  fun createNotification(@NotificationTitle title: String?,
                         @NotificationSubtitle subtitle: String?,
                         @NotificationContent content: String?,
                         type: NotificationType = NotificationType.INFORMATION,
                         listener: NotificationListener? = null): Notification {
    val result = Notification(displayId, content ?: "", type)
    result.setTitle(title, subtitle)
    if (listener != null) {
      @Suppress("DEPRECATION")
      result.setListener(listener)
    }
    return result
  }
  //</editor-fold>

  fun setParentId(value: String): NotificationGroup {
    parentId = value
    return this
  }
}

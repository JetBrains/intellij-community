// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification

import com.intellij.ide.plugins.PluginUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.util.NlsContexts.*
import com.intellij.util.containers.ContainerUtil
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
                                            val icon: Icon? = null,
                                            @NotificationTitle private var title: String? = null,
                                            pluginId: PluginId? = null,
                                            registerGroup: Boolean = false) {
  //<editor-fold desc="Deprecated stuff.">
  @Deprecated("Use com.intellij.notification.impl.NotificationGroupEP and com.intellij.notification.NotificationGroupManager")
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  constructor(displayId: String,
              displayType: NotificationDisplayType,
              isLogByDefault: Boolean = true,
              toolWindowId: String? = null,
              icon: Icon? = null,
              @NotificationTitle title: String? = null,
              pluginId: PluginId? = null) :
    this(displayId = displayId, displayType = displayType, isLogByDefault = isLogByDefault, toolWindowId = toolWindowId, icon = icon,
         title = title, pluginId = pluginId, registerGroup = true)

  // Don't use @JvmOverloads for primary constructor to maintain binary API compatibility with plugins written in Kotlin
  @JvmOverloads
  @Deprecated("Use com.intellij.notification.impl.NotificationGroupEP and com.intellij.notification.NotificationGroupManager")
  constructor(displayId: String,
              displayType: NotificationDisplayType,
              isLogByDefault: Boolean = true,
              toolWindowId: String? = null,
              icon: Icon? = null) :
    this(displayId = displayId, displayType = displayType, isLogByDefault = isLogByDefault, toolWindowId = toolWindowId, icon = icon,
         registerGroup = true)
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
    @JvmStatic
    fun create(displayId: String,
               displayType: NotificationDisplayType,
               isLogByDefault: Boolean,
               toolWindowId: String?,
               icon: Icon?,
               @NotificationTitle title: String?,
               pluginId: PluginId?): NotificationGroup {
      return NotificationGroup(displayId, displayType, isLogByDefault, toolWindowId, icon, title, pluginId, false)
    }

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
      return NotificationGroup(displayId, NotificationDisplayType.BALLOON, title = title)
    }

    @JvmStatic
    @Deprecated("Use com.intellij.notification.impl.NotificationGroupEP and com.intellij.notification.NotificationGroupManager")
    @Suppress("DEPRECATION")
    fun balloonGroup(displayId: String, pluginId: PluginId): NotificationGroup {
      val notificationGroup = findRegisteredNotificationGroup(displayId)
      if (notificationGroup != null) {
        return notificationGroup
      }
      return NotificationGroup(displayId, NotificationDisplayType.BALLOON, pluginId = pluginId)
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
      return NotificationGroup(displayId, NotificationDisplayType.NONE, title = title)
    }

    @JvmStatic
    @Deprecated("Use com.intellij.notification.impl.NotificationGroupEP and com.intellij.notification.NotificationGroupManager")
    @Suppress("DEPRECATION")
    fun logOnlyGroup(displayId: String, pluginId: PluginId): NotificationGroup {
      val notificationGroup = findRegisteredNotificationGroup(displayId)
      if (notificationGroup != null) {
        return notificationGroup
      }
      return NotificationGroup(displayId, NotificationDisplayType.NONE, pluginId = pluginId)
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

    @JvmOverloads
    @JvmStatic
    @Deprecated("Use com.intellij.notification.impl.NotificationGroupEP and com.intellij.notification.NotificationGroupManager")
    @Suppress("DEPRECATION")
    fun toolWindowGroup(displayId: String,
                        toolWindowId: String,
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
    @Suppress("DEPRECATION")
    fun toolWindowGroup(displayId: String, toolWindowId: String, logByDefault: Boolean, pluginId: PluginId): NotificationGroup {
      val notificationGroup = findRegisteredNotificationGroup(displayId)
      if (notificationGroup != null) {
        return notificationGroup
      }
      return NotificationGroup(displayId, NotificationDisplayType.TOOL_WINDOW, logByDefault, toolWindowId, pluginId = pluginId)
    }
    //</editor-fold>

    @JvmStatic
    fun findRegisteredGroup(displayId: String): NotificationGroup? =
      findRegisteredNotificationGroup(displayId) ?: registeredGroups[displayId]

    private fun findRegisteredNotificationGroup(displayId: String): NotificationGroup? =
      if (ApplicationManager.getApplication() == null) null
      else NotificationGroupManager.getInstance().getNotificationGroup(displayId)

    @JvmStatic
    fun getGroupTitle(displayId: String): String? =
      findRegisteredGroup(displayId)?.title ?: registeredTitles[displayId]

    @JvmStatic
    @Deprecated("Use `<notificationGroup>` extension point to register notification groups")
    fun createIdWithTitle(displayId: String, @NotificationTitle title: String): String {
      val oldTitle = registeredTitles.put(displayId, title)
      if (oldTitle != null && oldTitle != title) {
        logger<NotificationGroup>().error("NotificationGroup('${displayId}', '${oldTitle}') was re-created with different title '${title}'")
      }
      return displayId
    }

    @JvmStatic
    val allRegisteredGroups: Iterable<NotificationGroup>
      get() = ContainerUtil.concat(NotificationGroupManager.getInstance().registeredNotificationGroups, registeredGroups.values)
  }

  fun createNotification(@NotificationContent content: String, type: MessageType): Notification =
    createNotification(content, type.toNotificationType())

  fun createNotification(@NotificationContent content: String, type: NotificationType): Notification =
    createNotification("", content, type)

  fun createNotification(@NotificationTitle title: String, @NotificationContent content: String, type: NotificationType): Notification =
    Notification(displayId, title, content, type)

  //<editor-fold desc="Deprecated stuff.">
  @Deprecated("Use `createNotification(String, String, NotificationType)` along with `Notification#setListener`")
  @Suppress("DeprecatedCallableAddReplaceWith")
  fun createNotification(@NotificationTitle title: String,
                         @NotificationContent content: String,
                         type: NotificationType = NotificationType.INFORMATION,
                         listener: NotificationListener? = null): Notification =
    createNotification(title, content, type)
      .also { if (listener != null) it.setListener(listener) }

  @Deprecated("Use `createNotification(String, String, NotificationType)` along with `Notification#setDisplayId` and `Notification#setListener`")
  @Suppress("DeprecatedCallableAddReplaceWith")
  fun createNotification(@NotificationTitle title: String,
                         @NotificationContent content: String,
                         type: NotificationType = NotificationType.INFORMATION,
                         listener: NotificationListener? = null,
                         notificationDisplayId: String? = null): Notification =
    createNotification(title, content, type)
      .also { if (notificationDisplayId != null) it.setDisplayId(notificationDisplayId) }
      .also { if (listener != null) it.setListener(listener) }

  @Deprecated("Use `createNotification(String, NotificationType)` or `createNotification(String, String, NotificationType)`")
  @Suppress("DeprecatedCallableAddReplaceWith")
  @JvmOverloads
  fun createNotification(type: NotificationType = NotificationType.INFORMATION): Notification =
    Notification(displayId, "", type)

  @Deprecated("Use `createNotification(String, NotificationType)` or `createNotification(String, String, NotificationType)`" +
              " along with `Notification#setSubtitle` and `Notification#setListener`")
  @Suppress("DeprecatedCallableAddReplaceWith")
  @JvmOverloads
  fun createNotification(@NotificationTitle title: String?,
                         @NotificationSubtitle subtitle: String?,
                         @NotificationContent content: String?,
                         type: NotificationType = NotificationType.INFORMATION,
                         listener: NotificationListener? = null): Notification =
    Notification(displayId, content ?: "", type)
      .setTitle(title, subtitle)
      .also { if (listener != null) it.setListener(listener) }
  //</editor-fold>

  fun setParentId(value: String): NotificationGroup {
    parentId = value
    return this
  }
}

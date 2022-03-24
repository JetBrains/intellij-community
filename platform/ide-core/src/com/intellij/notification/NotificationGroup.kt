// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
 * Notification groups allow controlling display options in settings.
 * <p>
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
    @JvmStatic
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
    @ApiStatus.ScheduledForRemoval
    @Suppress("DEPRECATION")
    fun balloonGroup(displayId: String): NotificationGroup =
      findRegisteredNotificationGroup(displayId) ?: NotificationGroup(displayId, NotificationDisplayType.BALLOON)

    @JvmStatic
    @Deprecated("Use com.intellij.notification.impl.NotificationGroupEP and com.intellij.notification.NotificationGroupManager")
    @ApiStatus.ScheduledForRemoval
    @Suppress("DEPRECATION")
    fun balloonGroup(displayId: String, @NotificationTitle title: String?): NotificationGroup =
      findRegisteredNotificationGroup(displayId)
      ?: NotificationGroup(displayId = displayId, displayType = NotificationDisplayType.BALLOON, title = title, registerGroup = true)

    @JvmStatic
    @Deprecated("Use com.intellij.notification.impl.NotificationGroupEP and com.intellij.notification.NotificationGroupManager")
    @ApiStatus.ScheduledForRemoval
    @Suppress("DEPRECATION")
    fun balloonGroup(displayId: String, pluginId: PluginId): NotificationGroup =
      findRegisteredNotificationGroup(displayId)
      ?: NotificationGroup(displayId, NotificationDisplayType.BALLOON, pluginId = pluginId, registerGroup = true)

    @JvmStatic
    @Deprecated("Use com.intellij.notification.impl.NotificationGroupEP and com.intellij.notification.NotificationGroupManager")
    @ApiStatus.ScheduledForRemoval
    @Suppress("DEPRECATION")
    fun logOnlyGroup(displayId: String): NotificationGroup =
      findRegisteredNotificationGroup(displayId) ?: NotificationGroup(displayId, NotificationDisplayType.NONE)

    @JvmStatic
    @Deprecated("Use com.intellij.notification.impl.NotificationGroupEP and com.intellij.notification.NotificationGroupManager")
    @ApiStatus.ScheduledForRemoval
    @Suppress("DEPRECATION")
    fun logOnlyGroup(displayId: String, @NotificationTitle title: String?): NotificationGroup =
      findRegisteredNotificationGroup(displayId)
      ?: NotificationGroup(displayId, NotificationDisplayType.NONE, title = title, registerGroup = true)

    @JvmStatic
    @Deprecated("Use com.intellij.notification.impl.NotificationGroupEP and com.intellij.notification.NotificationGroupManager")
    @ApiStatus.ScheduledForRemoval
    @Suppress("DEPRECATION")
    fun logOnlyGroup(displayId: String, pluginId: PluginId): NotificationGroup =
      findRegisteredNotificationGroup(displayId)
      ?: NotificationGroup(displayId, NotificationDisplayType.NONE, pluginId = pluginId, registerGroup = true)

    @JvmOverloads
    @JvmStatic
    @Deprecated("Use com.intellij.notification.impl.NotificationGroupEP and com.intellij.notification.NotificationGroupManager")
    @ApiStatus.ScheduledForRemoval
    @Suppress("DEPRECATION")
    fun toolWindowGroup(displayId: String, toolWindowId: String, logByDefault: Boolean = true): NotificationGroup =
      findRegisteredNotificationGroup(displayId)
      ?: NotificationGroup(displayId, NotificationDisplayType.TOOL_WINDOW, logByDefault, toolWindowId)

    @JvmStatic
    @Deprecated("Use com.intellij.notification.impl.NotificationGroupEP and com.intellij.notification.NotificationGroupManager")
    @ApiStatus.ScheduledForRemoval
    @Suppress("DEPRECATION")
    fun toolWindowGroup(displayId: String, toolWindowId: String, logByDefault: Boolean, pluginId: PluginId): NotificationGroup =
      findRegisteredNotificationGroup(displayId)
      ?: NotificationGroup(displayId, NotificationDisplayType.TOOL_WINDOW, logByDefault, toolWindowId, pluginId = pluginId, registerGroup = true)

    @JvmStatic
    @Deprecated("Use `<notificationGroup>` extension point to register notification groups")
    @ApiStatus.ScheduledForRemoval
    fun createIdWithTitle(displayId: String, @NotificationTitle title: String): String {
      val oldTitle = registeredTitles.put(displayId, title)
      if (oldTitle != null && oldTitle != title) {
        logger<NotificationGroup>().error("NotificationGroup('${displayId}', '${oldTitle}') was re-created with different title '${title}'")
      }
      return displayId
    }
    //</editor-fold>

    @JvmStatic
    fun findRegisteredGroup(displayId: String): NotificationGroup? =
      findRegisteredNotificationGroup(displayId) ?: registeredGroups[displayId]

    @JvmStatic
    fun isGroupRegistered(displayId: String): Boolean =
      registeredGroups.containsKey(displayId) || NotificationGroupManager.getInstance().isGroupRegistered(displayId)

    private fun findRegisteredNotificationGroup(displayId: String): NotificationGroup? =
      ApplicationManager.getApplication()
        ?.getService(NotificationGroupManager::class.java)
        ?.getNotificationGroup(displayId)

    @JvmStatic
    fun getGroupTitle(displayId: String): String? =
      findRegisteredGroup(displayId)?.title ?: registeredTitles[displayId]

    val allRegisteredGroups: Iterable<NotificationGroup>
      get() = (NotificationGroupManager.getInstance().registeredNotificationGroups.asSequence() + registeredGroups.values).asIterable()
  }

  fun createNotification(@NotificationContent content: String, type: MessageType): Notification =
    createNotification(content = content, type = type.toNotificationType())

  fun createNotification(@NotificationContent content: String, type: NotificationType): Notification =
    createNotification("", content, type)

  fun createNotification(@NotificationTitle title: String, @NotificationContent content: String, type: NotificationType): Notification =
    Notification(displayId, title, content, type)

  //<editor-fold desc="Deprecated stuff.">
  @Deprecated("Use `createNotification(String, String, NotificationType)` along with `Notification#setListener`")
  @ApiStatus.ScheduledForRemoval
  @Suppress("DeprecatedCallableAddReplaceWith", "DEPRECATION")
  fun createNotification(@NotificationTitle title: String,
                         @NotificationContent content: String,
                         type: NotificationType = NotificationType.INFORMATION,
                         listener: NotificationListener? = null): Notification =
    createNotification(title, content, type)
      .also { if (listener != null) it.setListener(listener) }

  @Deprecated("Use `createNotification(String, String, NotificationType)` along with `Notification#setDisplayId` and `Notification#setListener`")
  @ApiStatus.ScheduledForRemoval
  @Suppress("DeprecatedCallableAddReplaceWith", "DEPRECATION")
  fun createNotification(@NotificationTitle title: String,
                         @NotificationContent content: String,
                         type: NotificationType = NotificationType.INFORMATION,
                         listener: NotificationListener? = null,
                         notificationDisplayId: String? = null): Notification =
    createNotification(title, content, type)
      .also { if (notificationDisplayId != null) it.setDisplayId(notificationDisplayId) }
      .also { if (listener != null) it.setListener(listener) }

  @Deprecated("Use `createNotification(String, NotificationType)` or `createNotification(String, String, NotificationType)`")
  @ApiStatus.ScheduledForRemoval
  @Suppress("DeprecatedCallableAddReplaceWith")
  @JvmOverloads
  fun createNotification(type: NotificationType = NotificationType.INFORMATION): Notification =
    Notification(displayId, "", type)

  @Deprecated("Use `createNotification(String, NotificationType)` or `createNotification(String, String, NotificationType)`" +
              " along with `Notification#setSubtitle` and `Notification#setListener`")
  @ApiStatus.ScheduledForRemoval
  @Suppress("DeprecatedCallableAddReplaceWith", "DEPRECATION")
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

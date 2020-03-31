// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification

import com.intellij.ide.plugins.PluginUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.ui.MessageType
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

private val LOG = logger<NotificationGroup>()
private val registeredGroups: MutableMap<String, NotificationGroup> = ContainerUtil.newConcurrentMap()

/**
 * Groups notifications and allows controlling display options in Settings.
 */
class NotificationGroup(@param:NonNls val displayId: String,
                        val displayType: NotificationDisplayType,
                        val isLogByDefault: Boolean = true,
                        @param:NonNls val toolWindowId: String? = null,
                        val icon: Icon? = null,
                        pluginId: PluginId? = null) {

  // Don't use @JvmOverloads for primary constructor to maintain binary API compatibility with plugins written in Kotlin
  @JvmOverloads
  constructor(@NonNls displayId: String,
              displayType: NotificationDisplayType,
              isLogByDefault: Boolean = true,
              @NonNls toolWindowId: String? = null,
              icon: Icon? = null) : this(displayId, displayType, isLogByDefault, toolWindowId, icon, null) {
  }

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
  }

  companion object {
    @JvmStatic
    fun balloonGroup(@NonNls displayId: String): NotificationGroup {
      return NotificationGroup(displayId, NotificationDisplayType.BALLOON)
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
    fun logOnlyGroup(@NonNls displayId: String, pluginId: PluginId): NotificationGroup {
      return NotificationGroup(displayId, NotificationDisplayType.NONE, pluginId = pluginId)
    }

    @JvmOverloads
    @JvmStatic
    fun toolWindowGroup(@NonNls displayId: String, @NonNls toolWindowId: String, logByDefault: Boolean = true): NotificationGroup {
      return NotificationGroup(displayId, NotificationDisplayType.TOOL_WINDOW, logByDefault, toolWindowId)
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
    val allRegisteredGroups: Iterable<NotificationGroup>
      get() = registeredGroups.values
  }

  fun createNotification(
    @Nls(capitalization = Nls.Capitalization.Sentence) content: String,
    type: MessageType
  ): Notification {
    return createNotification(content, type.toNotificationType())
  }

  fun createNotification(
    @Nls(capitalization = Nls.Capitalization.Sentence) content: String,
    type: NotificationType
  ): Notification {
    return createNotification("", content, type)
  }

  fun createNotification(
    @Nls(capitalization = Nls.Capitalization.Sentence) title: String,
    @Nls(capitalization = Nls.Capitalization.Sentence) content: String,
    type: NotificationType = NotificationType.INFORMATION,
    listener: NotificationListener? = null
  ): Notification {
    return Notification(displayId, title, content, type, listener)
  }

  @JvmOverloads
  fun createNotification(type: NotificationType = NotificationType.INFORMATION): Notification {
    return createNotification(title = null, subtitle = null, content = null, type = type)
  }

  @JvmOverloads
  fun createNotification(
    @Nls(capitalization = Nls.Capitalization.Sentence) title: String?,
    @Nls(capitalization = Nls.Capitalization.Sentence) subtitle: String?,
    @Nls(capitalization = Nls.Capitalization.Sentence) content: String?,
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
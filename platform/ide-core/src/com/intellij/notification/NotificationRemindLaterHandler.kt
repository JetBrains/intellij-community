// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification

import com.intellij.configurationStore.jdomSerializer
import com.intellij.configurationStore.serialize
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.extensions.ExtensionPointName
import org.jdom.Element

/**
 * @author Alexander Lobas
 */
interface NotificationRemindLaterHandler {
  companion object {
    private val EP_NAME: ExtensionPointName<NotificationRemindLaterHandler> = ExtensionPointName(
      "com.intellij.notificationRemindLaterHandler")

    @JvmStatic
    fun findHandler(handlerId: String): NotificationRemindLaterHandler? {
      for (handler in EP_NAME.extensionList) {
        if (handlerId == handler.getID()) {
          return handler
        }
      }
      return null
    }
  }

  fun getID(): String

  fun getActionsState(notification: Notification): Element

  fun setActions(notification: Notification, element: Element): Boolean
}

abstract class NotificationRemindLaterHandlerWithState<T : BaseState>(private val myType: Class<T>) : NotificationRemindLaterHandler {
  abstract fun getState(notification: Notification): T

  abstract fun setState(notification: Notification, state: T): Boolean

  override fun getActionsState(notification: Notification): Element {
    return serialize(getState(notification), createElementIfEmpty = true)!!
  }

  override fun setActions(notification: Notification, element: Element): Boolean {
    return setState(notification, jdomSerializer.deserialize(element, myType))
  }
}
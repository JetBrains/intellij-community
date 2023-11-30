// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

/**
 * Interface for a notification router.
 *
 * This interface defines a method for routing a notification to some notification manager
 * Implementations of this interface can be registered as extension points.
 */
interface NotificationRouter {

  /**
   * Routes a notification to some notification manager to be processed
   *
   * @param notification The notification to be routed.
   * @param project The project notification should be shown for
   * @return Returns true if the notification is routed to some manager, false otherwise.
   */
  fun routeNotification(notification: Notification, project: Project?): Boolean

  companion object {
    val EP_NAME: ExtensionPointName<NotificationRouter> = ExtensionPointName("com.intellij.notificationRouter")
  }
}

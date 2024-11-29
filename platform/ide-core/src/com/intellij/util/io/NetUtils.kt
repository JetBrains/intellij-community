// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io

import com.intellij.ide.IdeCoreBundle
import com.intellij.notification.BrowseNotificationAction
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import java.net.NoRouteToHostException

/**
 * @author Alexander Lobas
 */
@Internal
object NetUtils {
  private const val URL = "https://youtrack.jetbrains.com/articles/SUPPORT-A-564/Cannot-connect-to-remote-host-No-route-to-host-macOS-15-Sequoia"

  fun getNetworkErrorSolutionMessage(error: Throwable, full: Boolean): @Nls String? {
    if (SystemInfo.isMacOSSequoia && error is NoRouteToHostException) {
      return IdeCoreBundle.message(if (full) "mac15.local.network.issue.full.message" else "mac15.local.network.issue.message")
    }
    return null
  }

  @Suppress("DialogTitleCapitalization")
  fun showNetworkErrorSolutionNotification(error: Throwable, project: Project?) {
    if (SystemInfo.isMacOSSequoia && error is NoRouteToHostException) {
      Notification("Mac15 Local Network",
                   IdeCoreBundle.message("mac15.local.network.issue.title"),
                   IdeCoreBundle.message("mac15.local.network.issue.notification.message"),
                   NotificationType.INFORMATION)
        .addAction(BrowseNotificationAction(IdeCoreBundle.message("mac15.local.network.issue.notification.button"), URL))
        .notify(project)
    }
  }
}
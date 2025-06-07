// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io

import com.intellij.ide.IdeCoreBundle
import com.intellij.notification.BrowseNotificationAction
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import java.net.NoRouteToHostException

/**
 * @author Alexander Lobas
 */
@Internal
object NetUtils {
  const val URL = "https://youtrack.jetbrains.com/articles/SUPPORT-A-564/Cannot-connect-to-remote-host-No-route-to-host-macOS-15-Sequoia"

  private fun isNoRouteException(error: Throwable) = error is NoRouteToHostException || error.cause is NoRouteToHostException

  fun getNetworkErrorSolutionMessage(error: Throwable, full: Boolean): @Nls String? {
    if (SystemInfo.isMacOSSequoia && isNoRouteException(error)) {
      return IdeCoreBundle.message(if (full) "mac15.local.network.issue.full.message" else "mac15.local.network.issue.message")
    }
    return null
  }

  fun showNetworkErrorSolutionNotification(error: Throwable, project: Project?) {
    if (Registry.`is`("mac15.local.network.issue", true) && SystemInfo.isMacOSSequoia && isNoRouteException(error)) {
      Notification("Mac15 Local Network",
                   IdeCoreBundle.message("mac15.local.network.issue.title"),
                   IdeCoreBundle.message("mac15.local.network.issue.notification.message"),
                   NotificationType.INFORMATION)
        .addAction(BrowseNotificationAction(IdeCoreBundle.message("mac15.local.network.issue.notification.button"), URL))
        .notify(project)
    }
  }
}
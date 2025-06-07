// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.Version
import com.intellij.openapi.util.registry.Registry
import com.sun.jna.Library
import com.sun.jna.Native

private val MIN_SUPPORTED_GLIBC_VERSION: Version = Version(2, 28, 0)

internal class UnsupportedGlibcNotifierActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (SystemInfo.isLinux && Registry.`is`("ide.warn.glibc.version.unsupported")) {
      val glibc = Native.load("c", LibC::class.java)
      val libcVersionString = glibc.gnu_get_libc_version()

      val version = Version.parseVersion(libcVersionString)
      if (version == null) {
        thisLogger().warn("Failed to parse the glibc version: $libcVersionString")
        return
      }

      if (version < MIN_SUPPORTED_GLIBC_VERSION) {
        thisLogger().warn("Incompatible glibc version: $libcVersionString; showing a warning to user")

        val notification = NotificationGroupManager.getInstance()
          .getNotificationGroup("System Messages")
          .createNotification(
            DiagnosticBundle.message("notification.glibc.is.not.supported.title"),
            DiagnosticBundle.message("notification.glibc.is.not.supported.text",
                                     version,
                                     ApplicationNamesInfo.getInstance().fullProductName),
            NotificationType.WARNING
          )

        notification.setDisplayId("glibc.incompatible")
        notification.isSuggestionType = true
        notification.isImportant = true

        notification.addAction(NotificationAction.createSimple(DiagnosticBundle.message("notification.glibc.is.not.supported.button")) {
          BrowserUtil.browse("https://blog.jetbrains.com/idea/2025/01/updated-system-requirements-for-linux-gnu-c-library-glibc")
        })

        notification.notify(project)
      }
    }
  }
}

@Suppress("FunctionName")
private interface LibC : Library {
  fun gnu_get_libc_version(): String
}

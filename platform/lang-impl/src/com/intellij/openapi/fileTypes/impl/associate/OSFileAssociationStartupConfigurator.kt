// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes.impl.associate

import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.ApplicationActivity
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileTypes.FileTypesBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.util.messages.SimpleMessageBusConnection
import org.jetbrains.annotations.Nls

private class OSFileAssociationStartupConfigurator : ApplicationActivity {
  override suspend fun execute() {
    val preferences = serviceAsync<OSFileAssociationPreferences>()
    if (preferences.fileTypeNames.isEmpty() || !preferences.ideLocationChanged()) {
      return
    }

    logger<OSFileAssociationStartupConfigurator>().info("Restoring file type associations on IDE location change")
    val connection = ApplicationManager.getApplication().getMessageBus().simpleConnect()
    val resultHandler = MyResultHandler(connection)
    connection.subscribe(AppLifecycleListener.TOPIC, resultHandler)
    connection.subscribe(ProjectManager.TOPIC, resultHandler)
    OSAssociateFileTypesUtil.restoreAssociations(resultHandler)
    preferences.updateIdeLocationHash()
  }
}

private const val NOTIFICATION_GROUP_ID = "os.file.ide.association"

private val notificationTitle: @Nls String
  get() = FileTypesBundle.message("filetype.associate.notif.title")

private class MyResultHandler(private var connection: SimpleMessageBusConnection?)
  : OSAssociateFileTypesUtil.Callback, AppLifecycleListener, ProjectManagerListener {
  private var notification: Notification? = null

  override fun beforeStart() {}

  override fun onSuccess(isOsRestartRequired: Boolean) {
    logger<OSFileAssociationStartupConfigurator>().info("File-IDE associations successfully restored.")
    notification = Notification(NOTIFICATION_GROUP_ID, notificationTitle,
                                  FileTypesBundle.message("filetype.associate.notif.success",
                                                          ApplicationInfo.getInstance().getFullApplicationName()) +
                                  if (isOsRestartRequired) {
                                    """
                                   
                                   ${FileTypesBundle.message("filetype.associate.message.os.restart")}
                                   """.trimIndent()
                                  }
                                  else {
                                    ""
                                  },
                                if (isOsRestartRequired) NotificationType.WARNING else NotificationType.INFORMATION)
  }

  override fun onFailure(errorMessage: @Nls String) {
    logger<OSFileAssociationStartupConfigurator>().warn("File-IDE associations can't be restored: $errorMessage")
    notification = Notification(NOTIFICATION_GROUP_ID, notificationTitle,
                                FileTypesBundle.message("filetype.associate.notif.error"),
                                NotificationType.ERROR)
  }

  override fun welcomeScreenDisplayed() {
    doNotify(null)
  }

  override fun projectOpened(project: Project) {
    doNotify(project)
  }

  private fun doNotify(project: Project?) {
    if (notification != null) {
      Notifications.Bus.notify(notification!!, project)
      connection!!.disconnect()
      connection = null
      notification = null
    }
  }
}

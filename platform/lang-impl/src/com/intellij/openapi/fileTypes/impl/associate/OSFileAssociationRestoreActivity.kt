// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes.impl.associate

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileTypes.FileTypesBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import org.jetbrains.annotations.Nls
import java.util.concurrent.atomic.AtomicBoolean

private val LOG = logger<OSFileAssociationRestoreActivity>()

private const val NOTIFICATION_GROUP_ID = "os.file.ide.association"

/**
 * Restores the saved OS file type associations after the IDE location changes.
 * Runs once for each IDE session, when the first project opens.
 */
internal class OSFileAssociationRestoreActivity : ProjectActivity {
  private val started = AtomicBoolean()

  override suspend fun execute(project: Project) {
    if (!started.compareAndSet(false, true)) {
      return
    }

    val preferences = serviceAsync<OSFileAssociationPreferences>()
    if (preferences.fileTypeNames.isEmpty() || !preferences.ideLocationChanged()) {
      return
    }

    LOG.info("Restoring file type associations on IDE location change")
    OSAssociateFileTypesUtil.restoreAssociations(ResultHandler(project))
    preferences.updateIdeLocationHash()
  }
}

private class ResultHandler(private val project: Project) : OSAssociateFileTypesUtil.Callback {
  override fun beforeStart() {}

  override fun onSuccess(isOsRestartRequired: Boolean) {
    LOG.info("File-IDE associations successfully restored.")
    var content = FileTypesBundle.message("filetype.associate.notif.success", ApplicationInfo.getInstance().getFullApplicationName())
    if (isOsRestartRequired) {
      content += "\n" + FileTypesBundle.message("filetype.associate.message.os.restart")
    }
    notify(content, if (isOsRestartRequired) NotificationType.WARNING else NotificationType.INFORMATION)
  }

  override fun onFailure(errorMessage: @Nls String) {
    LOG.warn("File-IDE associations can't be restored: $errorMessage")
    notify(FileTypesBundle.message("filetype.associate.notif.error"), NotificationType.ERROR)
  }

  private fun notify(content: @Nls String, type: NotificationType) {
    val notification = Notification(NOTIFICATION_GROUP_ID, FileTypesBundle.message("filetype.associate.notif.title"), content, type)
    // the association runs on a pooled thread, and the project can close before it ends
    Notifications.Bus.notify(notification, project.takeUnless { it.isDisposed })
  }
}

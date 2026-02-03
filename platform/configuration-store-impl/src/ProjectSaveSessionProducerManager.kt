// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.notification.Notifications
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.UnableToSaveProjectNotification
import com.intellij.openapi.vfs.VirtualFile

internal suspend fun validateSaveResult(saveResult: SaveResult, project: Project) {
  val notifications = getUnableToSaveNotifications(project)
  val readonlyFiles = saveResult.readonlyFiles
  if (readonlyFiles.isEmpty()) {
    notifications.forEach(UnableToSaveProjectNotification::expire)
    return
  }

  if (notifications.isNotEmpty()) {
    throw UnresolvedReadOnlyFilesException(readonlyFiles.map { it.file })
  }

  val status = ensureFilesWritable(project, getFileList(readonlyFiles))
  if (status.hasReadonlyFiles()) {
    val unresolvedReadOnlyFiles = status.readonlyFiles.toList()
    dropUnableToSaveProjectNotification(project, unresolvedReadOnlyFiles)
    saveResult.addError(UnresolvedReadOnlyFilesException(unresolvedReadOnlyFiles))
    return
  }

  val oldList = readonlyFiles.toTypedArray()
  readonlyFiles.clear()
  saveSessions(saveSessions = oldList.map { it.session }, saveResult = saveResult, collectVfsEvents = true)

  if (readonlyFiles.isNotEmpty()) {
    dropUnableToSaveProjectNotification(project = project, readOnlyFiles = getFileList(readonlyFiles))
    saveResult.addError(UnresolvedReadOnlyFilesException(readonlyFiles.map { it.file }))
  }
}

private fun dropUnableToSaveProjectNotification(project: Project, readOnlyFiles: List<VirtualFile>) {
  val notifications = getUnableToSaveNotifications(project)
  if (notifications.isEmpty()) {
    Notifications.Bus.notify(UnableToSaveProjectNotification(project, readOnlyFiles), project)
  }
  else {
    notifications[0].files = readOnlyFiles
  }
}

private fun getUnableToSaveNotifications(project: Project): Array<out UnableToSaveProjectNotification> {
  return serviceIfCreated<NotificationsManager>()?.getNotificationsOfType(UnableToSaveProjectNotification::class.java, project) ?: emptyArray()
}

private fun getFileList(readonlyFiles: List<SaveSessionAndFile>): List<VirtualFile> = readonlyFiles.map { it.file }

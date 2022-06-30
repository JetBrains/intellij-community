// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.notification.Notifications
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.impl.stores.SaveSessionAndFile
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.UnableToSaveProjectNotification
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.SmartList
import com.intellij.util.containers.mapSmart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
open class ProjectSaveSessionProducerManager(protected val project: Project) : SaveSessionProducerManager() {
  suspend fun saveWithAdditionalSaveSessions(extraSessions: List<SaveSession>): SaveResult {
    val saveSessions = SmartList<SaveSession>()
    collectSaveSessions(saveSessions)
    if (saveSessions.isEmpty() && extraSessions.isEmpty()) {
      return SaveResult.EMPTY
    }

    val saveResult = withContext(Dispatchers.EDT + ModalityState.NON_MODAL.asContextElement()) {
      runWriteAction {
        val r = SaveResult()
        saveSessions(extraSessions, r)
        saveSessions(saveSessions, r)
        r
      }
    }
    validate(saveResult)
    return saveResult
  }

  private suspend fun validate(saveResult: SaveResult) {
    val notifications = getUnableToSaveNotifications()
    val readonlyFiles = saveResult.readonlyFiles
    if (readonlyFiles.isEmpty()) {
      notifications.forEach { it.expire() }
      return
    }

    if (notifications.isNotEmpty()) {
      throw UnresolvedReadOnlyFilesException(readonlyFiles.mapSmart { it.file })
    }

    val status = ensureFilesWritable(project, getFilesList(readonlyFiles))
    if (status.hasReadonlyFiles()) {
      val unresolvedReadOnlyFiles = status.readonlyFiles.toList()
      dropUnableToSaveProjectNotification(project, unresolvedReadOnlyFiles)
      saveResult.addError(UnresolvedReadOnlyFilesException(unresolvedReadOnlyFiles))
      return
    }

    val oldList = readonlyFiles.toTypedArray()
    readonlyFiles.clear()
    withContext(Dispatchers.EDT) {
      runWriteAction {
        val r = SaveResult()
        for (entry in oldList) {
          executeSave(entry.session, r)
        }
        r
      }
    }.appendTo(saveResult)

    if (readonlyFiles.isNotEmpty()) {
      dropUnableToSaveProjectNotification(project, getFilesList(readonlyFiles))
      saveResult.addError(UnresolvedReadOnlyFilesException(readonlyFiles.mapSmart { it.file }))
    }
  }

  private fun dropUnableToSaveProjectNotification(project: Project, readOnlyFiles: List<VirtualFile>) {
    val notifications = getUnableToSaveNotifications()
    if (notifications.isEmpty()) {
      Notifications.Bus.notify(UnableToSaveProjectNotification(project, readOnlyFiles), project)
    }
    else {
      notifications[0].files = readOnlyFiles
    }
  }

  private fun getUnableToSaveNotifications(): Array<out UnableToSaveProjectNotification> {
    val notificationManager = serviceIfCreated<NotificationsManager>() ?: return emptyArray()
    return notificationManager.getNotificationsOfType(UnableToSaveProjectNotification::class.java, project)
  }
}

private fun getFilesList(readonlyFiles: List<SaveSessionAndFile>) = readonlyFiles.mapSmart { it.file }
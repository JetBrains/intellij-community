// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.notification.Notifications
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.impl.stores.SaveSessionAndFile
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectManagerImpl
import com.intellij.openapi.vfs.ReadonlyStatusHandler
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.mapSmart
import com.intellij.util.lang.CompoundRuntimeException

internal class ProjectSaveSessionProducerManager(private val project: Project) : SaveSessionProducerManager() {
  override suspend fun save(readonlyFiles: MutableList<SaveSessionAndFile>, errors: MutableList<Throwable>): Boolean {
    val isChanged = super.save(readonlyFiles, errors)

    val notifications = getUnableToSaveNotifications()
    if (readonlyFiles.isEmpty()) {
      notifications.forEach { it.expire() }
      return isChanged
    }

    if (!notifications.isEmpty()) {
      throw IComponentStore.SaveCancelledException()
    }

    val status = runReadAction {
      ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(getFilesList(readonlyFiles))
    }
    if (status.hasReadonlyFiles()) {
      dropUnableToSaveProjectNotification(project, status.readonlyFiles.toList())
      throw IComponentStore.SaveCancelledException()
    }

    val oldList = readonlyFiles.toTypedArray()
    readonlyFiles.clear()
    for (entry in oldList) {
      executeSave(entry.session, readonlyFiles, errors)
    }

    CompoundRuntimeException.throwIfNotEmpty(errors)

    if (!readonlyFiles.isEmpty()) {
      dropUnableToSaveProjectNotification(project, getFilesList(readonlyFiles))
      throw IComponentStore.SaveCancelledException()
    }

    return isChanged
  }

  private fun dropUnableToSaveProjectNotification(project: Project, readOnlyFiles: List<VirtualFile>) {
    val notifications = getUnableToSaveNotifications()
    if (notifications.isEmpty()) {
      Notifications.Bus.notify(
        ProjectManagerImpl.UnableToSaveProjectNotification(project, readOnlyFiles), project)
    }
    else {
      notifications[0].setFiles(readOnlyFiles)
    }
  }

  private fun getUnableToSaveNotifications(): Array<out ProjectManagerImpl.UnableToSaveProjectNotification> {
    return NotificationsManager.getNotificationsManager()
      .getNotificationsOfType(ProjectManagerImpl.UnableToSaveProjectNotification::class.java, project)
  }
}

private fun getFilesList(readonlyFiles: List<SaveSessionAndFile>) = readonlyFiles.mapSmart { it.file }
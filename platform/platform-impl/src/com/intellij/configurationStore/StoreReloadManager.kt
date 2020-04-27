// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

interface StoreReloadManager {
  companion object {
    @JvmStatic
    fun getInstance() = service<StoreReloadManager>()
  }

  fun reloadProject(project: Project)

  fun blockReloadingProjectOnExternalChanges()

  fun unblockReloadingProjectOnExternalChanges()

  @ApiStatus.Internal
  fun isReloadBlocked(): Boolean

  @ApiStatus.Internal
  fun scheduleProcessingChangedFiles()

  @TestOnly
  fun flushChangedProjectFileAlarm()

  fun saveChangedProjectFile(file: VirtualFile, project: Project)

  suspend fun reloadChangedStorageFiles()

  fun storageFilesChanged(componentManagerToStorages: Map<ComponentManager, Collection<StateStorage>>)
}
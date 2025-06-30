// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface StoreReloadManager {
  companion object {
    @RequiresBlockingContext
    fun getInstance(project: Project): StoreReloadManager = project.service<StoreReloadManager>()
  }

  fun reloadProject()

  fun blockReloadingProjectOnExternalChanges()

  fun unblockReloadingProjectOnExternalChanges()

  fun isReloadBlocked(): Boolean

  fun scheduleProcessingChangedFiles()

  fun saveChangedProjectFile(file: VirtualFile)

  suspend fun reloadChangedStorageFiles()

  fun storageFilesChanged(store: IComponentStore, storages: Collection<StateStorage>)
  
  fun storageFilesBatchProcessing(batchStorageEvents: Map<IComponentStore, Collection<StateStorage>>)
}
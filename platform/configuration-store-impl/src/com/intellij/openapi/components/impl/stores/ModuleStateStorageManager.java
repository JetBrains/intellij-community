/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.configurationStore

import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.StateStorageOperation
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.TrackingPathMacroSubstitutor
import com.intellij.openapi.module.Module
import com.intellij.util.containers.ContainerUtil

class ModuleStateStorageManager(pathMacroManager: TrackingPathMacroSubstitutor, private val myModule: Module) : StateStorageManagerImpl(pathMacroManager, "module", myModule, myModule.getPicoContainer()) {
  override fun createStorageData(fileSpec: String, filePath: String) = ModuleFileData(rootTagName, myModule)

  override fun startExternalization() = MyStateStorageManagerExternalizationSession(this)

  private class MyStateStorageManagerExternalizationSession(storageManager: StateStorageManagerImpl) : StateStorageManagerImpl.StateStorageManagerExternalizationSession(storageManager) {
    override fun createSaveSessions(): List<StateStorage.SaveSession> {
      val storage = ContainerUtil.getFirstItem(storageManager.getCachedFileStorages(listOf(StoragePathMacros.MODULE_FILE)))
      if (storage != null && storage.getStorageData().isDirty()) {
        // force XmlElementStorageSaveSession creation
        getExternalizationSession(storage)
      }
      return super.createSaveSessions()
    }
  }

  override fun getOldStorageSpec(component: Any, componentName: String, operation: StateStorageOperation) = StoragePathMacros.MODULE_FILE

  override fun createStorageTopicListener() = myModule.getProject().getMessageBus().syncPublisher(StateStorage.PROJECT_STORAGE_TOPIC)
}
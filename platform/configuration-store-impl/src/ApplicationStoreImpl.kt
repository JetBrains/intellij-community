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

import com.intellij.application.options.PathMacrosImpl
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.StateStorageOperation
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.impl.BasePathMacroManager
import com.intellij.openapi.components.impl.stores.DirectoryStorageData
import com.intellij.openapi.components.impl.stores.StateStorageManager
import com.intellij.openapi.components.impl.stores.StorageData
import com.intellij.openapi.util.NamedJDOMExternalizable
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil

class ApplicationPathMacroManager : BasePathMacroManager(null)

class ApplicationStoreImpl(private val application: ApplicationImpl, pathMacroManager: PathMacroManager) : ComponentStoreImpl() {
  private val stateStorageManager: StateStorageManager

  companion object {
    private val DEFAULT_STORAGE_SPEC = "${StoragePathMacros.APP_CONFIG}/${PathManager.DEFAULT_OPTIONS_FILE_NAME}${DirectoryStorageData.DEFAULT_EXT}"
  }

  init {
    stateStorageManager = object : StateStorageManagerImpl(pathMacroManager.createTrackingSubstitutor(), "application", application, application.getPicoContainer()) {
      private var configDirectoryRefreshed = false

      override fun createStorageTopicListener() = application.getMessageBus().syncPublisher(StateStorage.STORAGE_TOPIC)

      override fun createStorageData(fileSpec: String, filePath: String) = StorageData(rootTagName)

      override fun getOldStorageSpec(component: Any, componentName: String, operation: StateStorageOperation): String? {
        if (component is NamedJDOMExternalizable) {
          return "${StoragePathMacros.APP_CONFIG}/${component.getExternalFileName()}${DirectoryStorageData.DEFAULT_EXT}"
        }
        else {
          return DEFAULT_STORAGE_SPEC
        }
      }

      override fun getMacroSubstitutor(fileSpec: String) = if (fileSpec == "${StoragePathMacros.APP_CONFIG}/${PathMacrosImpl.EXT_FILE_NAME}${DirectoryStorageData.DEFAULT_EXT}") null else super.getMacroSubstitutor(fileSpec)

      override fun isUseXmlProlog() = false

      override fun beforeFileBasedStorageCreate() {
        if (configDirectoryRefreshed || (!application.isUnitTestMode() && !application.isDispatchThread())) {
          return
        }

        try {
          val configPath = expandMacros(StoragePathMacros.ROOT_CONFIG)
          if (configPath == StoragePathMacros.ROOT_CONFIG) {
            LOG.warn("Macros ROOT_CONFIG is not defined")
            return
          }

          val configDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(configPath)
          if (configDir != null) {
            VfsUtil.markDirtyAndRefresh(false, true, true, configDir)
          }
        }
        finally {
          configDirectoryRefreshed = true
        }
      }
    }
  }

  override fun getMessageBus() = application.getMessageBus()

  override fun getStateStorageManager() = stateStorageManager
}

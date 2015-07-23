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
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.NamedJDOMExternalizable
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.messages.MessageBus

public class ApplicationPathMacroManager : BasePathMacroManager(null)

class ApplicationStoreImpl(private val myApplication: ApplicationImpl, pathMacroManager: PathMacroManager) : ComponentStoreImpl() {
  private val myStateStorageManager: StateStorageManager

  init {
    myStateStorageManager = object : StateStorageManagerImpl(pathMacroManager.createTrackingSubstitutor(), ROOT_ELEMENT_NAME, myApplication, myApplication.getPicoContainer()) {
      private var myConfigDirectoryRefreshed: Boolean = false

      override fun createStorageTopicListener(): StateStorage.Listener? {
        return myApplication.getMessageBus().syncPublisher(StateStorage.STORAGE_TOPIC)
      }

      override fun createStorageData(fileSpec: String, filePath: String): StorageData {
        return StorageData(ROOT_ELEMENT_NAME)
      }

      override fun getOldStorageSpec(component: Any, componentName: String, operation: StateStorageOperation): String? {
        if (component is NamedJDOMExternalizable) {
          return StoragePathMacros.APP_CONFIG + '/' + component.getExternalFileName() + DirectoryStorageData.DEFAULT_EXT
        }
        else {
          return DEFAULT_STORAGE_SPEC
        }
      }

      override fun getMacroSubstitutor(fileSpec: String) = if (fileSpec == StoragePathMacros.APP_CONFIG + '/' + PathMacrosImpl.EXT_FILE_NAME + DirectoryStorageData.DEFAULT_EXT) null else super.getMacroSubstitutor(fileSpec)

      override fun isUseXmlProlog() = false

      override fun beforeFileBasedStorageCreate() {
        if (myConfigDirectoryRefreshed || (!myApplication.isUnitTestMode() && !myApplication.isDispatchThread())) {
          return
        }

        try {
          val configPath = getMacrosValue(StoragePathMacros.ROOT_CONFIG)
          if (configPath == null) {
            LOG.warn("Macros ROOT_CONFIG is not defined")
            return
          }

          val configDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(configPath)
          if (configDir != null) {
            VfsUtil.markDirtyAndRefresh(false, true, true, configDir)
          }
        }
        finally {
          myConfigDirectoryRefreshed = true
        }
      }
    }
  }

  override fun getMessageBus(): MessageBus {
    return myApplication.getMessageBus()
  }

  override fun getStateStorageManager(): StateStorageManager {
    return myStateStorageManager
  }

  companion object {
    private val LOG = Logger.getInstance(javaClass<ApplicationStoreImpl>())

    private val DEFAULT_STORAGE_SPEC = StoragePathMacros.APP_CONFIG + "/" + PathManager.DEFAULT_OPTIONS_FILE_NAME + DirectoryStorageData.DEFAULT_EXT
    private val ROOT_ELEMENT_NAME = "application"
  }
}

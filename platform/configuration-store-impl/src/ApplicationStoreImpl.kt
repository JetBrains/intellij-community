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
import com.intellij.openapi.application.invokeAndWaitIfNeed
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.components.StateStorageOperation
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.impl.BasePathMacroManager
import com.intellij.openapi.components.impl.ServiceManagerImpl
import com.intellij.openapi.components.impl.stores.DirectoryStorageData
import com.intellij.openapi.util.NamedJDOMExternalizable
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil

class ApplicationPathMacroManager : BasePathMacroManager(null)

class ApplicationStoreImpl(private val application: ApplicationImpl, pathMacroManager: PathMacroManager) : ComponentStoreImpl() {
  override val storageManager = object : StateStorageManagerImpl("application", pathMacroManager.createTrackingSubstitutor(), application) {
    override fun getOldStorageSpec(component: Any, componentName: String, operation: StateStorageOperation): String? {
      if (component is NamedJDOMExternalizable) {
        return "${StoragePathMacros.APP_CONFIG}/${component.getExternalFileName()}${DirectoryStorageData.DEFAULT_EXT}"
      }
      else {
        return DEFAULT_STORAGE_SPEC
      }
    }

    override fun getMacroSubstitutor(fileSpec: String) = if (fileSpec == "${StoragePathMacros.APP_CONFIG}/${PathMacrosImpl.EXT_FILE_NAME}${DirectoryStorageData.DEFAULT_EXT}") null else super.getMacroSubstitutor(fileSpec)

    override protected val isUseXmlProlog: Boolean
      get() = false
  }

  companion object {
    private val DEFAULT_STORAGE_SPEC = "${StoragePathMacros.APP_CONFIG}/${PathManager.DEFAULT_OPTIONS_FILE_NAME}${DirectoryStorageData.DEFAULT_EXT}"

    private val FILE_STORAGE_DIR = "options"
  }

  override fun setPath(path: String) {
    storageManager.addMacro(ROOT_CONFIG, path)
    storageManager.addMacro(StoragePathMacros.APP_CONFIG, "$path/$FILE_STORAGE_DIR")

    val configDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(path)
    if (configDir != null) {
      invokeAndWaitIfNeed {
        // not recursive, config directory contains various data - for example, ICS or shelf should not be refreshed,
        // but we refresh direct children to avoid refreshAndFindFile in SchemeManager (to find schemes directory)

        // ServiceManager inits service under read-action, so, we cannot refresh scheme dir on SchemeManager creation because it leads to error "Calling invokeAndWait from read-action leads to possible deadlock."
        val refreshAll = ServiceManagerImpl.isUseReadActionToInitService()

        VfsUtil.markDirtyAndRefresh(false, refreshAll, true, configDir)
        val optionsDir = configDir.findChild(FILE_STORAGE_DIR)
        if (!refreshAll && optionsDir != null) {
          // not recursive, options directory contains only files
          VfsUtil.markDirtyAndRefresh(false, false, true, optionsDir)
        }
      }
    }
  }

  override fun getMessageBus() = application.getMessageBus()
}
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
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.invokeAndWaitIfNeed
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.components.StateStorageOperation
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.impl.BasePathMacroManager
import com.intellij.openapi.components.impl.ServiceManagerImpl
import com.intellij.openapi.components.impl.stores.FileStorageCoreUtil
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.NamedJDOMExternalizable
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import org.jdom.Element

class ApplicationPathMacroManager : BasePathMacroManager(null)

class ApplicationStoreImpl(private val application: Application, pathMacroManager: PathMacroManager) : ComponentStoreImpl() {
  override val storageManager = ApplicationStorageManager(application, pathMacroManager)

  override fun setPath(path: String) {
    storageManager.addMacro(ROOT_CONFIG, path)
    storageManager.addMacro(StoragePathMacros.APP_CONFIG, "$path/${ApplicationStorageManager.FILE_STORAGE_DIR}")

    val configDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(path)
    if (configDir != null) {
      invokeAndWaitIfNeed {
        // not recursive, config directory contains various data - for example, ICS or shelf should not be refreshed,
        // but we refresh direct children to avoid refreshAndFindFile in SchemeManager (to find schemes directory)

        // ServiceManager inits service under read-action, so, we cannot refresh scheme dir on SchemeManager creation because it leads to error "Calling invokeAndWait from read-action leads to possible deadlock."
        val refreshAll = ServiceManagerImpl.isUseReadActionToInitService()

        VfsUtil.markDirtyAndRefresh(false, refreshAll, true, configDir)
        val optionsDir = configDir.findChild(ApplicationStorageManager.FILE_STORAGE_DIR)
        if (!refreshAll && optionsDir != null) {
          // not recursive, options directory contains only files
          VfsUtil.markDirtyAndRefresh(false, false, true, optionsDir)
        }
      }
    }
  }
}

class ApplicationStorageManager(private val application: Application, pathMacroManager: PathMacroManager? = null) : StateStorageManagerImpl("application", pathMacroManager?.createTrackingSubstitutor(), application) {
  companion object {
    private val DEFAULT_STORAGE_SPEC = "${PathManager.DEFAULT_OPTIONS_FILE_NAME}${FileStorageCoreUtil.DEFAULT_EXT}"

    val FILE_STORAGE_DIR = "options"
  }

  override fun getOldStorageSpec(component: Any, componentName: String, operation: StateStorageOperation): String? {
    if (component is NamedJDOMExternalizable) {
      return "${component.getExternalFileName()}${FileStorageCoreUtil.DEFAULT_EXT}"
    }
    else {
      return DEFAULT_STORAGE_SPEC
    }
  }

  override fun getMacroSubstitutor(fileSpec: String) = if (fileSpec == "${PathMacrosImpl.EXT_FILE_NAME}${FileStorageCoreUtil.DEFAULT_EXT}") null else super.getMacroSubstitutor(fileSpec)

  override protected val isUseXmlProlog: Boolean
    get() = false

  override fun dataLoadedFromProvider(storage: FileBasedStorage, element: Element?) {
    // IDEA-144052 When "Settings repository" is enabled changes in 'Path Variables' aren't saved to default path.macros.xml file causing errors in build process
    try {
      if (element == null) {
        storage.file.delete()
      }
      else {
        FileUtilRt.createParentDirs(storage.file)
        JDOMUtil.writeElement(element, storage.file.writer(), "\n")
      }
    }
    catch (e: Throwable) {
      LOG.error(e)
    }
  }

  override fun normalizeFileSpec(fileSpec: String): String {
    var path = super.normalizeFileSpec(fileSpec)
    if (path.startsWithMacro(StoragePathMacros.APP_CONFIG)) {
      return path.substring(StoragePathMacros.APP_CONFIG.length() + 1)
    }
    return path
  }

  override fun fileSpecToPath(fileSpec: String): String {
    if (fileSpec[0] == '$') {
      return super.fileSpecToPath(fileSpec)
    }
    else {
      return "${expandMacro(StoragePathMacros.APP_CONFIG)}/$fileSpec"
    }
  }
}
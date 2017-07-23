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
import com.intellij.openapi.components.impl.BasePathMacroManager
import com.intellij.openapi.components.impl.stores.FileStorageCoreUtil
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.util.NamedJDOMExternalizable
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.io.delete
import com.intellij.util.io.outputStream
import com.intellij.util.write
import org.jdom.Element

private class ApplicationPathMacroManager : BasePathMacroManager(null)

const val APP_CONFIG = "\$APP_CONFIG$"
private val FILE_STORAGE_DIR = "options"

class ApplicationStoreImpl(private val application: Application, pathMacroManager: PathMacroManager? = null) : ComponentStoreImpl() {
  override val storageManager = ApplicationStorageManager(application, pathMacroManager)

  // number of app components require some state, so, we load default state in test mode
  override val loadPolicy: StateLoadPolicy
    get() = if (application.isUnitTestMode) StateLoadPolicy.LOAD_ONLY_DEFAULT else StateLoadPolicy.LOAD

  override fun setPath(path: String) {
    // app config must be first, because collapseMacros collapse from fist to last, so, at first we must replace APP_CONFIG because it overlaps ROOT_CONFIG value
    storageManager.addMacro(APP_CONFIG, "$path/${FILE_STORAGE_DIR}")
    storageManager.addMacro(ROOT_CONFIG, path)

    val configDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(path)
    if (configDir != null) {
      invokeAndWaitIfNeed {
        // not recursive, config directory contains various data - for example, ICS or shelf should not be refreshed,
        // but we refresh direct children to avoid refreshAndFindFile in SchemeManager (to find schemes directory)
        VfsUtil.markDirtyAndRefresh(false, false, true, configDir)
        val optionsDir = configDir.findChild(FILE_STORAGE_DIR)
        if (optionsDir != null) {
          // not recursive, options directory contains only files
          VfsUtil.markDirtyAndRefresh(false, false, true, optionsDir)
        }
      }
    }
  }
}

class ApplicationStorageManager(application: Application, pathMacroManager: PathMacroManager? = null) : StateStorageManagerImpl("application", pathMacroManager?.createTrackingSubstitutor(), application) {
  companion object {
    private val DEFAULT_STORAGE_SPEC = "${PathManager.DEFAULT_OPTIONS_FILE_NAME}${FileStorageCoreUtil.DEFAULT_EXT}"

  }

  override fun getOldStorageSpec(component: Any, componentName: String, operation: StateStorageOperation): String? {
    return if (component is NamedJDOMExternalizable) {
      "${component.externalFileName}${FileStorageCoreUtil.DEFAULT_EXT}"
    }
    else {
      DEFAULT_STORAGE_SPEC
    }
  }

  override fun getMacroSubstitutor(fileSpec: String) = if (fileSpec == "${PathMacrosImpl.EXT_FILE_NAME}${FileStorageCoreUtil.DEFAULT_EXT}") null else super.getMacroSubstitutor(fileSpec)

  override val isUseXmlProlog: Boolean
    get() = false

  override fun providerDataStateChanged(storage: FileBasedStorage, element: Element?, type: DataStateChanged) {
    // IDEA-144052 When "Settings repository" is enabled changes in 'Path Variables' aren't saved to default path.macros.xml file causing errors in build process
    if (storage.fileSpec != "path.macros.xml") {
      return
    }

    LOG.runAndLogException {
      if (element == null) {
        storage.file.delete()
      }
      else {
        element.write(storage.file.outputStream())
      }
    }
  }

  override fun normalizeFileSpec(fileSpec: String) = removeMacroIfStartsWith(super.normalizeFileSpec(fileSpec), APP_CONFIG)

  override fun expandMacros(path: String) = if (path[0] == '$') {
    super.expandMacros(path)
  }
  else {
    "${expandMacro(APP_CONFIG)}/$path"
  }
}
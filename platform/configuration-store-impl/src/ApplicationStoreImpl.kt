// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.configurationStore.schemeManager.ROOT_CONFIG
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.components.PathMacroSubstitutor
import com.intellij.openapi.components.StateStorageOperation
import com.intellij.openapi.components.impl.stores.FileStorageCoreUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.util.NamedJDOMExternalizable
import org.jetbrains.jps.model.serialization.JpsGlobalLoader

private class ApplicationPathMacroManager : PathMacroManager(null)

const val APP_CONFIG: String = "\$APP_CONFIG$"
private const val FILE_STORAGE_DIR = "options"
private const val DEFAULT_STORAGE_SPEC = "${PathManager.DEFAULT_OPTIONS_FILE_NAME}${FileStorageCoreUtil.DEFAULT_EXT}"

class ApplicationStoreImpl(private val application: Application, pathMacroManager: PathMacroManager? = null) : ComponentStoreWithExtraComponents() {
  override val storageManager: ApplicationStorageManager = ApplicationStorageManager(application, pathMacroManager)

  // number of app components require some state, so, we load default state in test mode
  override val loadPolicy: StateLoadPolicy
    get() = if (application.isUnitTestMode) StateLoadPolicy.LOAD_ONLY_DEFAULT else StateLoadPolicy.LOAD

  override fun setPath(path: String) {
    // app config must be first, because collapseMacros collapse from fist to last, so, at first we must replace APP_CONFIG because it overlaps ROOT_CONFIG value
    storageManager.addMacro(APP_CONFIG, "$path/${FILE_STORAGE_DIR}")
    storageManager.addMacro(ROOT_CONFIG, path)
  }

  override fun saveAdditionalComponents(isForce: Boolean) {
    // here, because no Project (and so, ProjectStoreImpl) on Welcome Screen
    service<DefaultProjectExportableAndSaveTrigger>().save(isForce)
  }

  override fun toString() = "app"
}

class ApplicationStorageManager(application: Application, pathMacroManager: PathMacroManager? = null)
  : StateStorageManagerImpl("application", pathMacroManager?.createTrackingSubstitutor(), application) {

  override fun getOldStorageSpec(component: Any, componentName: String, operation: StateStorageOperation): String? =
    if (component is NamedJDOMExternalizable) "${component.externalFileName}${FileStorageCoreUtil.DEFAULT_EXT}" else DEFAULT_STORAGE_SPEC

  override fun getMacroSubstitutor(fileSpec: String): PathMacroSubstitutor? =
    if (fileSpec == JpsGlobalLoader.PathVariablesSerializer.STORAGE_FILE_NAME) null else super.getMacroSubstitutor(fileSpec)

  override val isUseXmlProlog: Boolean
    get() = false

  override val isUseVfsForWrite: Boolean
    get() = false

  override fun providerDataStateChanged(storage: FileBasedStorage, writer: DataWriter?, type: DataStateChanged) {
    // IDEA-144052 When "Settings repository" is enabled changes in 'Path Variables' aren't saved to default path.macros.xml file causing errors in build process
    if (storage.fileSpec == "path.macros.xml") {
      LOG.runAndLogException {
        writer.writeTo(storage.file)
      }
    }
  }

  override fun normalizeFileSpec(fileSpec: String): String = removeMacroIfStartsWith(super.normalizeFileSpec(fileSpec), APP_CONFIG)

  override fun expandMacros(path: String): String = if (path[0] == '$') super.expandMacros(path) else "${expandMacro(APP_CONFIG)}/$path"
}
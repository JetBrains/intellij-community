// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.configurationStore.schemeManager.ROOT_CONFIG
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.appSystemDir
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.components.StateStorageOperation
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.NamedJDOMExternalizable
import com.intellij.serviceContainer.ComponentManagerImpl
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.NonNls
import java.nio.file.Path

internal class ApplicationPathMacroManager : PathMacroManager(null)

@NonNls const val APP_CONFIG = "\$APP_CONFIG$"

open class ApplicationStoreImpl : ComponentStoreWithExtraComponents() {
  override val storageManager = ApplicationStorageManager(ApplicationManager.getApplication(), PathMacroManager.getInstance(ApplicationManager.getApplication()))

  override val serviceContainer: ComponentManagerImpl
    get() = ApplicationManager.getApplication() as ComponentManagerImpl

  // number of app components require some state, so, we load default state in test mode
  override val loadPolicy: StateLoadPolicy
    get() = if (ApplicationManager.getApplication().isUnitTestMode) StateLoadPolicy.LOAD_ONLY_DEFAULT else StateLoadPolicy.LOAD

  override fun setPath(path: Path) {
    storageManager.setMacros(listOf(
      // app config must be first, because collapseMacros collapse from fist to last, so, at first we must replace APP_CONFIG because it overlaps ROOT_CONFIG value
      Macro(APP_CONFIG, path.resolve(PathManager.OPTIONS_DIRECTORY)),
      Macro(ROOT_CONFIG, path),
      Macro(StoragePathMacros.CACHE_FILE, appSystemDir.resolve("workspace").resolve("app.xml"))
    ))
  }

  override suspend fun doSave(result: SaveResult, forceSavingAllSettings: Boolean) {
    val saveSessionManager = createSaveSessionProducerManager()
    saveSettingsSavingComponentsAndCommitComponents(result, forceSavingAllSettings, saveSessionManager)
    // todo can we store default project in parallel to regular saving? for now only flush on disk is async, but not component committing
    coroutineScope {
      launch {
        saveSessionManager.save().appendTo(result)
      }

      if (ProjectManagerEx.getInstanceEx().isDefaultProjectInitialized) {
        launch {
          // here, because no Project (and so, ProjectStoreImpl) on Welcome Screen
          val r = service<DefaultProjectExportableAndSaveTrigger>().save(forceSavingAllSettings)
          // ignore
          r.isChanged = false
          r.appendTo(result)
        }
      }
    }
  }

  override fun toString() = "app"
}

internal val appFileBasedStorageConfiguration = object: FileBasedStorageConfiguration {
  override val isUseVfsForRead: Boolean
    get() = false

  override val isUseVfsForWrite: Boolean
    get() = false
}

class ApplicationStorageManager(application: Application?, pathMacroManager: PathMacroManager? = null)
  : StateStorageManagerImpl("application", pathMacroManager?.createTrackingSubstitutor (), application) {
  override fun getFileBasedStorageConfiguration(fileSpec: String) = appFileBasedStorageConfiguration

  override fun getOldStorageSpec(component: Any, componentName: String, operation: StateStorageOperation): String {
    return when (component) {
      is NamedJDOMExternalizable -> "${component.externalFileName}${PathManager.DEFAULT_EXT}"
      else -> StoragePathMacros.NON_ROAMABLE_FILE
    }
  }

  override val isUseXmlProlog: Boolean
    get() = false

  override fun providerDataStateChanged(storage: FileBasedStorage, writer: DataWriter?, type: DataStateChanged) {
    // IDEA-144052 When "Settings repository" is enabled changes in 'Path Variables' aren't saved to default path.macros.xml file causing errors in build process
    if (storage.fileSpec == "path.macros.xml" || storage.fileSpec == "applicationLibraries.xml") {
      LOG.runAndLogException {
        writer.writeTo(storage.file, null)
      }
    }
  }

  override fun normalizeFileSpec(fileSpec: String) = removeMacroIfStartsWith(super.normalizeFileSpec(fileSpec), APP_CONFIG)

  override fun expandMacro(collapsedPath: String): Path {
    // APP_CONFIG is the first macro
    return if (collapsedPath[0] == '$') super.expandMacro(collapsedPath) else macros.get(0).value.resolve(collapsedPath)
  }
}
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")
package com.intellij.configurationStore

import com.intellij.configurationStore.schemeManager.ROOT_CONFIG
import com.intellij.diagnostic.LoadingState
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.appSystemDir
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.components.StateStorageOperation
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.platform.settings.SettingsController
import com.intellij.platform.workspace.jps.serialization.impl.ApplicationStoreJpsContentReader
import com.intellij.platform.workspace.jps.serialization.impl.JpsAppFileContentWriter
import com.intellij.platform.workspace.jps.serialization.impl.JpsFileContentReader
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.util.LineSeparator
import com.intellij.workspaceModel.ide.JpsGlobalModelSynchronizer
import com.intellij.workspaceModel.ide.impl.jps.serialization.JpsGlobalModelSynchronizerImpl
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.Path

private class ApplicationPathMacroManager : PathMacroManager(null)

@NonNls const val APP_CONFIG = "\$APP_CONFIG$"

@Suppress("NonDefaultConstructor")
open class ApplicationStoreImpl(private val app: Application)
  : ComponentStoreWithExtraComponents(), ApplicationStoreJpsContentReader {
  override val storageManager = ApplicationStorageManager(pathMacroManager = PathMacroManager.getInstance(app),
                                                          settingsController = app.getService(SettingsController::class.java))

  override val serviceContainer: ComponentManagerImpl
    get() = app as ComponentManagerImpl

  // a number of app components require some state, so we load the default state in test mode
  override val loadPolicy: StateLoadPolicy
    get() = if (app.isUnitTestMode) StateLoadPolicy.LOAD_ONLY_DEFAULT else StateLoadPolicy.LOAD

  override fun setPath(path: Path) {
    storageManager.setMacros(listOf(
      // app config must be first, because collapseMacros collapse from fist to last, so,
      // at first we must replace APP_CONFIG because it overlaps ROOT_CONFIG value
      Macro(APP_CONFIG, path.resolve(PathManager.OPTIONS_DIRECTORY)),
      Macro(ROOT_CONFIG, path),
      Macro(StoragePathMacros.CACHE_FILE, appSystemDir.resolve("app-cache.xml"))
    ))

    if (!LoadingState.CONFIGURATION_STORE_INITIALIZED.isOccurred) {
      LoadingState.setCurrentState(LoadingState.CONFIGURATION_STORE_INITIALIZED)
    }
  }

  override suspend fun doSave(result: SaveResult, forceSavingAllSettings: Boolean) {
    val saveSessionManager = createSaveSessionProducerManager()
    (serviceAsync<JpsGlobalModelSynchronizer>() as JpsGlobalModelSynchronizerImpl).saveGlobalEntities()
    saveSettingsSavingComponentsAndCommitComponents(result, forceSavingAllSettings, saveSessionManager)
    // todo can we store default project in parallel to regular saving? for now only flush on disk is async, but not component committing
    coroutineScope {
      launch {
        saveSessionManager.save().appendTo(result)
      }

      @Suppress("TestOnlyProblems")
      if ((serviceAsync<ProjectManager>() as ProjectManagerEx).isDefaultProjectInitialized) {
        launch {
          // here, because no Project (and so, ProjectStoreImpl) on a Welcome Screen
          val r = serviceAsync<DefaultProjectExportableAndSaveTrigger>().save(forceSavingAllSettings)
          r.appendTo(result)
        }
      }
    }
  }

  override fun createContentWriter(): JpsAppFileContentWriter = AppStorageContentWriter(createSaveSessionProducerManager())

  override fun createContentReader(): JpsFileContentReader = AppStorageContentReader()

  override fun toString() = "app"
}

internal val appFileBasedStorageConfiguration = object: FileBasedStorageConfiguration {
  override val isUseVfsForRead: Boolean
    get() = false

  override val isUseVfsForWrite: Boolean
    get() = false
}

@VisibleForTesting
class ApplicationStorageManager(pathMacroManager: PathMacroManager? = null, settingsController: SettingsController?)
  : StateStorageManagerImpl(rootTagName = "application",
                            macroSubstitutor = pathMacroManager?.createTrackingSubstitutor(),
                            componentManager = null,
                            settingsController = settingsController) {
  override fun getFileBasedStorageConfiguration(fileSpec: String) = appFileBasedStorageConfiguration

  override fun getOldStorageSpec(component: Any, componentName: String, operation: StateStorageOperation): String {
    @Suppress("DEPRECATION")
    return when (component) {
      is com.intellij.openapi.util.NamedJDOMExternalizable -> "${component.externalFileName}${PathManager.DEFAULT_EXT}"
      else -> StoragePathMacros.NON_ROAMABLE_FILE
    }
  }

  override val isUseXmlProlog: Boolean
    get() = false

  override fun providerDataStateChanged(storage: FileBasedStorage, writer: DataWriter?, type: DataStateChanged) {
    if (storage.fileSpec == "path.macros.xml" || storage.fileSpec == "applicationLibraries.xml") {
      LOG.runAndLogException {
        writer.writeTo(storage.file, requestor = null, LineSeparator.LF, isUseXmlProlog)
      }
    }
  }

  override fun normalizeFileSpec(fileSpec: String) = removeMacroIfStartsWith(super.normalizeFileSpec(fileSpec), APP_CONFIG)

  override fun expandMacro(collapsedPath: String): Path {
    // APP_CONFIG is the first macro
    return if (collapsedPath[0] == '$') super.expandMacro(collapsedPath) else macros.get(0).value.resolve(collapsedPath)
  }
}

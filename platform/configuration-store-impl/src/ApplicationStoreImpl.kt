// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.configurationStore.schemeManager.ROOT_CONFIG
import com.intellij.diagnostic.LoadingState
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.appSystemDir
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.components.StateStorageOperation
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.impl.stores.stateStore
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.getOrLogException
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
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.Files
import java.nio.file.Path

@ApiStatus.Internal
const val APP_CONFIG: String = "\$APP_CONFIG\$"

@ApiStatus.Internal
@VisibleForTesting
@Suppress("NonDefaultConstructor")
open class ApplicationStoreImpl(private val app: Application) : ComponentStoreWithExtraComponents(), ApplicationStoreJpsContentReader {
  override val storageManager: StateStorageManagerImpl =
    ApplicationStateStorageManager(pathMacroManager = PathMacroManager.getInstance(app), controller = app.getService(SettingsController::class.java))

  override val allowSavingWithoutModifications: Boolean
    get() = true

  override val serviceContainer: ComponentManagerImpl
    get() = app as ComponentManagerImpl

  // a number of app components require some state, so we load the default state in test mode
  override val loadPolicy: StateLoadPolicy
    get() = if (app.isUnitTestMode) StateLoadPolicy.LOAD_ONLY_DEFAULT else StateLoadPolicy.LOAD

  final override fun setPath(path: Path) {
    @Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
    storageManager.setMacros(java.util.List.of(
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

  final override suspend fun doSave(saveResult: SaveResult, forceSavingAllSettings: Boolean) {
    (serviceAsync<JpsGlobalModelSynchronizer>() as JpsGlobalModelSynchronizerImpl).saveGlobalEntities()

    coroutineScope {
      launch {
        super.doSave(saveResult, forceSavingAllSettings)
      }

      val projectManager = serviceAsync<ProjectManager>() as ProjectManagerEx
      @Suppress("TestOnlyProblems")
      if (projectManager.isDefaultProjectInitialized) {
        launch {
          (projectManager.defaultProject.stateStore as ComponentStoreImpl).doSave(saveResult, forceSavingAllSettings)
        }
      }
    }
  }

  final override fun createContentWriter(): JpsAppFileContentWriter = AppStorageContentWriter(createSaveSessionProducerManager())

  final override fun createContentReader(): JpsFileContentReader = AppStorageContentReader()

  final override fun toString(): String = "app"
}

@ApiStatus.Internal
@VisibleForTesting
class ApplicationStateStorageManager(pathMacroManager: PathMacroManager? = null, controller: SettingsController?)
  : StateStorageManagerImpl(rootTagName = "application", pathMacroManager?.createTrackingSubstitutor(), componentManager = null, controller)
{
  override fun getOldStorageSpec(component: Any, componentName: String, operation: StateStorageOperation): String = StoragePathMacros.NON_ROAMABLE_FILE

  override val isUseXmlProlog: Boolean
    get() = false

  override fun providerDataStateChanged(storage: FileBasedStorage, writer: DataWriter?, type: DataStateChanged) {
    if (storage.fileSpec == "path.macros.xml" || storage.fileSpec == "applicationLibraries.xml") {
      runCatching {
        @Suppress("IfThenToElvis")
        if (writer == null) {
          Files.deleteIfExists(storage.file)
        }
        else {
          writer.writeTo(storage.file, requestor = null, LineSeparator.LF, isUseXmlProlog)
        }
      }.getOrLogException(LOG)
    }
  }

  override fun normalizeFileSpec(fileSpec: String): String =
    removeMacroIfStartsWith(path = super.normalizeFileSpec(fileSpec), macro = APP_CONFIG)

  override fun expandMacro(collapsedPath: String): Path =
    if (collapsedPath[0] == '$') super.expandMacro(collapsedPath)
    else macros[0].value.resolve(collapsedPath)  // APP_CONFIG is the first macro
}

private class ApplicationPathMacroManager : PathMacroManager(null)

@ApiStatus.Internal
fun removeMacroIfStartsWith(path: String, macro: String): String = path.removePrefix("$macro/")

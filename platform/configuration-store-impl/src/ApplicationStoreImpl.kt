// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")
package com.intellij.configurationStore

import com.intellij.configurationStore.schemeManager.ROOT_CONFIG
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.appSystemDir
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.components.StateStorageOperation
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.NamedJDOMExternalizable
import com.intellij.platform.workspace.jps.serialization.impl.ApplicationStoreJpsContentReader
import com.intellij.platform.workspace.jps.serialization.impl.JpsAppFileContentWriter
import com.intellij.platform.workspace.jps.serialization.impl.JpsFileContentReader
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.workspaceModel.ide.JpsGlobalModelSynchronizer
import com.intellij.workspaceModel.ide.impl.jps.serialization.JpsGlobalModelSynchronizerImpl
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.NonNls
import java.nio.file.Path

internal class ApplicationPathMacroManager : PathMacroManager(null)

@NonNls const val APP_CONFIG = "\$APP_CONFIG$"

@Suppress("NonDefaultConstructor")
open class ApplicationStoreImpl(private val app: Application)
  : ComponentStoreWithExtraComponents(), ApplicationStoreJpsContentReader {
  override val storageManager = ApplicationStorageManager(PathMacroManager.getInstance(app))

  override val serviceContainer: ComponentManagerImpl
    get() = app as ComponentManagerImpl

  // a number of app components require some state, so we load the default state in test mode
  override val loadPolicy: StateLoadPolicy
    get() = if (app.isUnitTestMode) StateLoadPolicy.LOAD_ONLY_DEFAULT else StateLoadPolicy.LOAD

  override fun setPath(path: Path) {
    storageManager.setMacros(listOf(
      // app config must be first, because collapseMacros collapse from fist to last, so, at first we must replace APP_CONFIG because it overlaps ROOT_CONFIG value
      Macro(APP_CONFIG, path.resolve(PathManager.OPTIONS_DIRECTORY)),
      Macro(ROOT_CONFIG, path),
      Macro(StoragePathMacros.CACHE_FILE, appSystemDir.resolve("app-cache.xml"))
    ))
  }

  override suspend fun doSave(result: SaveResult, forceSavingAllSettings: Boolean) {
    val saveSessionManager = createSaveSessionProducerManager()
    (JpsGlobalModelSynchronizer.getInstance() as JpsGlobalModelSynchronizerImpl).saveGlobalEntities()
    saveSettingsSavingComponentsAndCommitComponents(result, forceSavingAllSettings, saveSessionManager)
    // todo can we store default project in parallel to regular saving? for now only flush on disk is async, but not component committing
    coroutineScope {
      launch {
        saveSessionManager.save().appendTo(result)
      }

      @Suppress("TestOnlyProblems")
      if (ProjectManagerEx.getInstanceEx().isDefaultProjectInitialized) {
        launch {
          // here, because no Project (and so, ProjectStoreImpl) on a Welcome Screen
          val r = serviceAsync<DefaultProjectExportableAndSaveTrigger>().save(forceSavingAllSettings)
          // ignore
          r.isChanged = false
          r.appendTo(result)
        }
      }
    }
  }

  override fun createContentWriter(): JpsAppFileContentWriter {
    val saveSessionManager = createSaveSessionProducerManager()
    return AppStorageContentWriter(saveSessionManager)
  }

  override fun createContentReader(): JpsFileContentReader = AppStorageContentReader()

  override fun toString() = "app"
}

internal val appFileBasedStorageConfiguration = object: FileBasedStorageConfiguration {
  override val isUseVfsForRead: Boolean
    get() = false

  override val isUseVfsForWrite: Boolean
    get() = false
}

class ApplicationStorageManager(pathMacroManager: PathMacroManager? = null)
  : StateStorageManagerImpl(rootTagName = "application",
                            macroSubstitutor = pathMacroManager?.createTrackingSubstitutor(),
                            componentManager = null) {
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
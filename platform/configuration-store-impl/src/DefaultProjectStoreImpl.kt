// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.components.impl.stores.StoreUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectImpl
import org.jdom.Element
import java.nio.file.Path
import java.nio.file.Paths

private const val FILE_SPEC = "${APP_CONFIG}/project.default.xml"

private class DefaultProjectStorage(file: Path, fileSpec: String, pathMacroManager: PathMacroManager) : FileBasedStorage(file, fileSpec, "defaultProject", pathMacroManager.createTrackingSubstitutor(), RoamingType.DISABLED) {
  override public fun loadLocalData(): Element? {
    val element = super.loadLocalData() ?: return null
    try {
      return element.getChild("component").getChild("defaultProject")
    }
    catch (e: NullPointerException) {
      LOG.warn("Cannot read default project")
      return null
    }
  }

  override fun createSaveSession(states: StateMap) = object : FileBasedStorage.FileSaveSession(states, this) {
    override fun saveLocally(element: Element?) {
      super.saveLocally(element?.let {
        Element("application")
          .addContent(Element("component").setAttribute("name", "ProjectManager").addContent(it))
      })
    }
  }
}

// cannot be `internal`, used in Upsource
class DefaultProjectStoreImpl(override val project: ProjectImpl, private val pathMacroManager: PathMacroManager) : ComponentStoreImpl() {
  // see note about default state in project store
  override val loadPolicy: StateLoadPolicy
    get() = if (ApplicationManager.getApplication().isUnitTestMode) StateLoadPolicy.NOT_LOAD else StateLoadPolicy.LOAD

  init {
    service<DefaultProjectExportableAndSaveTrigger>().project = project
  }

  private val storage by lazy { DefaultProjectStorage(Paths.get(ApplicationManager.getApplication().stateStore.storageManager.expandMacros(FILE_SPEC)), FILE_SPEC, pathMacroManager) }

  override val storageManager: StateStorageManager = object : StateStorageManager {
    override val componentManager: ComponentManager?
      get() = null

    override fun addStreamProvider(provider: StreamProvider, first: Boolean) {
    }

    override fun removeStreamProvider(clazz: Class<out StreamProvider>) {
    }

    override fun rename(path: String, newName: String) {
    }

    override fun getStateStorage(storageSpec: Storage) = storage

    override fun startExternalization() = storage.startExternalization()?.let(::MyExternalizationSession)

    override fun expandMacros(path: String) = throw UnsupportedOperationException()

    override fun getOldStorage(component: Any, componentName: String, operation: StateStorageOperation) = storage
  }

  override fun isUseLoadedStateAsExisting(storage: StateStorage): Boolean = false

  // don't want to optimize and use already loaded data - it will add unnecessary complexity and implementation-lock (currently we store loaded archived state in memory, but later implementation can be changed)
  fun getStateCopy(): Element? = storage.loadLocalData()

  override fun getPathMacroManagerForDefaults(): PathMacroManager = pathMacroManager

  override fun <T> getStorageSpecs(component: PersistentStateComponent<T>, stateSpec: State, operation: StateStorageOperation): List<FileStorageAnnotation> = listOf(PROJECT_FILE_STORAGE_ANNOTATION)

  override fun setPath(path: String) {
  }
}

private class MyExternalizationSession(val externalizationSession: StateStorage.ExternalizationSession) : StateStorageManager.ExternalizationSession {
  override fun setState(storageSpecs: List<Storage>, component: Any, componentName: String, state: Any) {
    externalizationSession.setState(component, componentName, state)
  }

  override fun setStateInOldStorage(component: Any, componentName: String, state: Any) {
    externalizationSession.setState(component, componentName, state)
  }

  override fun createSaveSessions() = listOfNotNull(externalizationSession.createSaveSession())
}

// ExportSettingsAction checks only "State" annotation presence, but doesn't require PersistentStateComponent implementation, so, we can just specify annotation
@State(name = "ProjectManager", storages = [(Storage(FILE_SPEC))])
internal class DefaultProjectExportableAndSaveTrigger {
  @Volatile
  var project: Project? = null

  fun save(isForceSavingAllSettings: Boolean) {
    // we must trigger save
    StoreUtil.saveProject(project ?: return, isForceSavingAllSettings)
  }
}
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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
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

internal class DefaultProjectStoreImpl(override val project: ProjectImpl, private val pathMacroManager: PathMacroManager) : ComponentStoreImpl() {
  // see note about default state in project store
  override val loadPolicy: StateLoadPolicy
    get() = if (ApplicationManager.getApplication().isUnitTestMode) StateLoadPolicy.NOT_LOAD else StateLoadPolicy.LOAD

  init {
    service<DefaultProjectExportableAndSaveTrigger>().project = project
  }

  private val storage by lazy { DefaultProjectStorage(Paths.get(ApplicationManager.getApplication().stateStore.stateStorageManager.expandMacros(FILE_SPEC)), FILE_SPEC, pathMacroManager) }

  override val storageManager = object : StateStorageManager {
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

  override fun isUseLoadedStateAsExisting(storage: StateStorage) = false

  // don't want to optimize and use already loaded data - it will add unnecessary complexity and implementation-lock (currently we store loaded archived state in memory, but later implementation can be changed)
  fun getStateCopy() = storage.loadLocalData()
  
  override fun getPathMacroManagerForDefaults() = pathMacroManager

  override fun <T> getStorageSpecs(component: PersistentStateComponent<T>, stateSpec: State, operation: StateStorageOperation) = listOf(PROJECT_FILE_STORAGE_ANNOTATION)

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
@State(name = "ProjectManager", storages = arrayOf(Storage(FILE_SPEC)))
private class DefaultProjectExportableAndSaveTrigger : SettingsSavingComponent {
  @Volatile
  var project: Project? = null

  override fun save() {
    // we must trigger save
    project?.save()
  }
}
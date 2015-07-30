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
import com.intellij.openapi.components.impl.stores.FileBasedStorage
import com.intellij.openapi.components.impl.stores.StateStorageManager
import com.intellij.openapi.components.impl.stores.StreamProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.openapi.project.impl.ProjectManagerImpl
import com.intellij.openapi.util.Couple
import com.intellij.util.containers.ContainerUtil
import java.io.File
import kotlin.properties.Delegates

class DefaultProjectStoreImpl(override val project: ProjectImpl, private val pathMacroManager: PathMacroManager) : ComponentStoreImpl() {
  companion object {
    val FILE_SPEC = "${StoragePathMacros.APP_CONFIG}/project.default.xml"
  }

  init {
    service<DefaultProjectExportableAndSaveTrigger>()!!.project = project
  }

  private val storage by Delegates.lazy { DefaultProjectStorage(File(ApplicationManager.getApplication().stateStore.getStateStorageManager().expandMacros(FILE_SPEC)), FILE_SPEC, pathMacroManager) }

  private val storageManager = object : StateStorageManager {
    override fun getMacroSubstitutor() = null

    override fun getStateStorage(storageSpec: Storage) = storage

    override fun getStateStorage(fileSpec: String, roamingType: RoamingType) = storage

    override fun getCachedFileStateStorages(changed: Collection<String>, deleted: Collection<String>): Couple<Collection<FileBasedStorage>> = Couple(emptyList<FileBasedStorage>(), emptyList<FileBasedStorage>())

    override fun clearStateStorage(file: String) {
    }

    override fun startExternalization(): StateStorageManager.ExternalizationSession? {
      val externalizationSession = storage.startExternalization()
      return if (externalizationSession == null) null else MyExternalizationSession(externalizationSession)
    }

    override fun expandMacros(file: String) = throw UnsupportedOperationException("Method expandMacros not implemented in " + javaClass)

    override fun collapseMacros(path: String) = throw UnsupportedOperationException("Method collapseMacros not implemented in " + javaClass)

    override fun getOldStorage(component: Any, componentName: String, operation: StateStorageOperation) = storage

    override fun setStreamProvider(streamProvider: StreamProvider?) = throw UnsupportedOperationException("Method setStreamProvider not implemented in " + javaClass)

    override fun getStreamProvider() = throw UnsupportedOperationException("Method getStreamProviders not implemented in " + javaClass)

    override fun getStorageFileNames() = throw UnsupportedOperationException("Method getStorageFileNames not implemented in " + javaClass)
  }

  fun getStateCopy() = storage.loadLocalData()

  override fun getMessageBus() = project.getMessageBus()

  override final fun getStateStorageManager() = storageManager

  override final fun getPathMacroManagerForDefaults() = pathMacroManager

  override fun selectDefaultStorages(storages: Array<Storage>, operation: StateStorageOperation) = selectDefaultStorages(storages, operation, StorageScheme.DEFAULT)

  override fun setPath(path: String) {
  }

  private class MyExternalizationSession(val externalizationSession: StateStorage.ExternalizationSession) : StateStorageManager.ExternalizationSession {
    override fun setState(storageSpecs: Array<Storage>, component: Any, componentName: String, state: Any) {
      externalizationSession.setState(component, componentName, state, null)
    }

    override fun setStateInOldStorage(component: Any, componentName: String, state: Any) {
      externalizationSession.setState(component, componentName, state, null)
    }

    override fun createSaveSessions() = ContainerUtil.createMaybeSingletonList(externalizationSession.createSaveSession())
  }
}

// ExportSettingsAction checks only "State" annotation presence, but doesn't require PersistentStateComponent implementation, so, we can just specify annotation
State(name = "ProjectManager", storages = arrayOf(Storage(file = DefaultProjectStoreImpl.FILE_SPEC)))
private class DefaultProjectExportableAndSaveTrigger : SettingsSavingComponent {
  volatile var project: Project? = null;

  override fun save() {
    // we must trigger save
    project?.save()
  }
}
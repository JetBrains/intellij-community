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
package com.intellij.openapi.components.impl.stores

import com.intellij.openapi.components.*
import com.intellij.openapi.components.impl.stores.FileBasedStorage
import com.intellij.openapi.components.impl.stores.StateStorageManager
import com.intellij.openapi.components.impl.stores.StreamProvider
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.openapi.project.impl.ProjectManagerImpl
import com.intellij.openapi.util.Couple
import com.intellij.util.containers.ContainerUtil
import org.jdom.Element

public class DefaultProjectStoreImpl(project: ProjectImpl, private val projectManager: ProjectManagerImpl, pathMacroManager: PathMacroManager) : ProjectStoreImpl(project, pathMacroManager) {
  fun getStateCopy(): Element? {
    val element = projectManager.getDefaultProjectRootElement()
    return element?.clone()
  }

  protected override fun createStateStorageManager(): StateStorageManager {
    val storage = DefaultProjectStorage(this, myPathMacroManager, projectManager)
    //noinspection deprecation
    return object : StateStorageManager {
      override fun addMacro(macro: String, expansion: String) = throw UnsupportedOperationException("Method addMacro not implemented in " + javaClass)

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

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

import com.intellij.openapi.components.*
import com.intellij.openapi.components.impl.stores.ModuleStore
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.module.Module
import com.intellij.util.containers.computeIfAny
import com.intellij.util.io.exists
import java.nio.file.Paths

private val MODULE_FILE_STORAGE_ANNOTATION = FileStorageAnnotation(StoragePathMacros.MODULE_FILE, false)

private open class ModuleStoreImpl(module: Module, private val pathMacroManager: PathMacroManager) : ModuleStoreBase() {
  override val project = module.project

  override val storageManager = ModuleStateStorageManager(pathMacroManager.createTrackingSubstitutor(), module)

  override final fun getPathMacroManagerForDefaults() = pathMacroManager

  // todo what about Upsource? For now this implemented not in the ModuleStoreBase because `project` and `module` are available only in this class (ModuleStoreImpl)
  override fun <T> getStorageSpecs(component: PersistentStateComponent<T>, stateSpec: State, operation: StateStorageOperation): List<Storage> {
    val result =  super.getStorageSpecs(component, stateSpec, operation)
    return StreamProviderFactory.EP_NAME.getExtensions(project).computeIfAny {
      LOG.runAndLogException { it.customizeStorageSpecs(component, storageManager.componentManager!!, stateSpec, result, operation) }
    } ?: result
  }
}

private class TestModuleStore(module: Module, pathMacroManager: PathMacroManager) : ModuleStoreImpl(module, pathMacroManager) {
  private var moduleComponentLoadPolicy: StateLoadPolicy? = null

  override fun setPath(path: String, isNew: Boolean) {
    super.setPath(path, isNew)

    if (!isNew && Paths.get(path).exists()) {
      moduleComponentLoadPolicy = StateLoadPolicy.LOAD
    }
  }

  override val loadPolicy: StateLoadPolicy
    get() = moduleComponentLoadPolicy ?: (project.stateStore as ComponentStoreImpl).loadPolicy
}

// used in upsource
abstract class ModuleStoreBase : ComponentStoreImpl(), ModuleStore {
  override abstract val storageManager: StateStorageManagerImpl

  override fun <T> getStorageSpecs(component: PersistentStateComponent<T>, stateSpec: State, operation: StateStorageOperation): List<Storage> {
    val storages = stateSpec.storages
    return if (storages.isEmpty()) {
      listOf(MODULE_FILE_STORAGE_ANNOTATION)
    }
    else {
      super.getStorageSpecs(component, stateSpec, operation)
    }
  }

  override final fun setPath(path: String) {
    setPath(path, false)
  }

  override fun setPath(path: String, isNew: Boolean) {
    val isMacroAdded = storageManager.addMacro(StoragePathMacros.MODULE_FILE, path)
    // if file not null - update storage
    storageManager.getOrCreateStorage(StoragePathMacros.MODULE_FILE, storageCustomizer = {
      if (this !is FileBasedStorage) {
        // upsource
        return@getOrCreateStorage
      }

      setFile(null, if (isMacroAdded) null else Paths.get(path))
      // ModifiableModuleModel#newModule should always create a new module from scratch
      // https://youtrack.jetbrains.com/issue/IDEA-147530

      if (isMacroAdded) {
        // preload to ensure that we will get FileNotFound error (no module file) during init, and not later in some unexpected place (because otherwise will be loaded by demand)
        preloadStorageData(isNew)
      }
      else {
        storageManager.updatePath(StoragePathMacros.MODULE_FILE, path)
      }
    })
  }
}
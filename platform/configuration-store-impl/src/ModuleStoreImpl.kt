// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.openapi.components.*
import com.intellij.openapi.components.impl.stores.ModuleStore
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.project.isDirectoryBased
import com.intellij.util.io.exists
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Paths
import kotlin.streams.asSequence

private val MODULE_FILE_STORAGE_ANNOTATION = FileStorageAnnotation(StoragePathMacros.MODULE_FILE, false)

@ApiStatus.Internal
internal open class ModuleStoreImpl(module: Module) : ModuleStoreBase() {
  private val pathMacroManager = PathMacroManager.getInstance(module)

  override val project = module.project

  override val storageManager = ModuleStateStorageManager(TrackingPathMacroSubstitutorImpl(pathMacroManager), module)

  final override fun getPathMacroManagerForDefaults() = pathMacroManager

  // todo what about Upsource? For now this implemented not in the ModuleStoreBase because `project` and `module` are available only in this class (ModuleStoreImpl)
  override fun <T> getStorageSpecs(component: PersistentStateComponent<T>, stateSpec: State, operation: StateStorageOperation): List<Storage> {
    val result = super.getStorageSpecs(component, stateSpec, operation)
    if (!project.isDirectoryBased) {
      return result
    }
    return StreamProviderFactory.EP_NAME.extensions(project).asSequence()
             .map { LOG.runAndLogException { it.customizeStorageSpecs(component, storageManager, stateSpec, result, operation) } }
             .find { it != null } ?: result
  }
}

private class TestModuleStore(module: Module) : ModuleStoreImpl(module) {
  private var moduleComponentLoadPolicy: StateLoadPolicy? = null

  override fun setPath(path: String, virtualFile: VirtualFile?, isNew: Boolean) {
    super.setPath(path, virtualFile, isNew)
    if (!isNew && Paths.get(path).exists()) {
      moduleComponentLoadPolicy = StateLoadPolicy.LOAD
    }
  }

  override val loadPolicy: StateLoadPolicy
    get() = moduleComponentLoadPolicy ?: (project.stateStore as ComponentStoreImpl).loadPolicy
}

// used in upsource
abstract class ModuleStoreBase : ChildlessComponentStore(), ModuleStore {
  abstract override val storageManager: StateStorageManagerImpl

  override fun <T> getStorageSpecs(component: PersistentStateComponent<T>, stateSpec: State, operation: StateStorageOperation): List<Storage> =
    if (stateSpec.storages.isEmpty()) listOf(MODULE_FILE_STORAGE_ANNOTATION)
    else super.getStorageSpecs(component, stateSpec, operation)

  final override fun setPath(path: String) = setPath(path, null, false)

  override fun setPath(path: String, virtualFile: VirtualFile?, isNew: Boolean) {
    val isMacroAdded = storageManager.addMacro(StoragePathMacros.MODULE_FILE, path)
    // if file not null - update storage
    storageManager.getOrCreateStorage(StoragePathMacros.MODULE_FILE, storageCustomizer = {
      if (this !is FileBasedStorage) {
        // upsource
        return@getOrCreateStorage
      }

      setFile(virtualFile, if (isMacroAdded) null else Paths.get(path))
      // ModifiableModuleModel#newModule should always create a new module from scratch
      // https://youtrack.jetbrains.com/issue/IDEA-147530

      if (isMacroAdded) {
        // preload to ensure that we will get FileNotFound error (no module file) during initialization,
        // and not later in some unexpected place (because otherwise will be loaded by demand)
        preloadStorageData(isNew)
      }
      else {
        storageManager.updatePath(StoragePathMacros.MODULE_FILE, path)
      }
    })
  }
}
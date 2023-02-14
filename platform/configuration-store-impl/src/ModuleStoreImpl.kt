// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.components.*
import com.intellij.openapi.components.impl.stores.ModuleStore
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.project.ProjectStoreOwner
import com.intellij.project.isDirectoryBased
import com.intellij.util.messages.MessageBus
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.exists

private val MODULE_FILE_STORAGE_ANNOTATION = FileStorageAnnotation(StoragePathMacros.MODULE_FILE, false)

@ApiStatus.Internal
internal open class ModuleStoreImpl(module: Module) : ModuleStoreBase() {
  private val pathMacroManager = PathMacroManager.getInstance(module)
  override val project = module.project

  override val storageManager = ModuleStateStorageManager(TrackingPathMacroSubstitutorImpl(pathMacroManager), module)

  final override fun getPathMacroManagerForDefaults() = pathMacroManager

  override fun <T> getStorageSpecs(component: PersistentStateComponent<T>, stateSpec: State, operation: StateStorageOperation): List<Storage> {
    val result = super.getStorageSpecs(component, stateSpec, operation)
    if (!project.isDirectoryBased) {
      return result
    }

    for (provider in StreamProviderFactory.EP_NAME.getExtensions(project)) {
      LOG.runAndLogException {
        provider.customizeStorageSpecs(component, storageManager, stateSpec, result, operation)?.let {
          return it
        }
      }
    }
    return result
  }

  final override fun reloadStates(componentNames: Set<String>, messageBus: MessageBus) {
    runBatchUpdate(project) {
      reinitComponents(componentNames)
    }
  }
}

private class TestModuleStore(module: Module) : ModuleStoreImpl(module) {
  private var moduleComponentLoadPolicy: StateLoadPolicy? = null

  override fun setPath(path: Path, virtualFile: VirtualFile?, isNew: Boolean) {
    super.setPath(path, virtualFile, isNew)
    if (!isNew && path.exists()) {
      moduleComponentLoadPolicy = StateLoadPolicy.LOAD
    }
  }

  override val loadPolicy: StateLoadPolicy
    get() = moduleComponentLoadPolicy ?: ((project as ProjectStoreOwner).componentStore as ComponentStoreImpl).loadPolicy
}

// used in upsource
abstract class ModuleStoreBase : ChildlessComponentStore(), ModuleStore {
  final override fun isReportStatisticAllowed(stateSpec: State, storageSpec: Storage) = false

  abstract override val storageManager: StateStorageManagerImpl

  override fun <T> getStorageSpecs(component: PersistentStateComponent<T>, stateSpec: State, operation: StateStorageOperation): List<Storage> {
    if (stateSpec.storages.isEmpty()) {
      return listOf(MODULE_FILE_STORAGE_ANNOTATION)
    }
    else {
      return super.getStorageSpecs(component, stateSpec, operation)
    }
  }

  final override fun setPath(path: Path) = setPath(path, null, false)

  override fun setPath(path: Path, virtualFile: VirtualFile?, isNew: Boolean) {
    val isMacroAdded = storageManager.setMacros(listOf(Macro(StoragePathMacros.MODULE_FILE, path))).isEmpty()
    // if file not null - update storage
    storageManager.getOrCreateStorage(StoragePathMacros.MODULE_FILE, RoamingType.DEFAULT, storageCustomizer = {
      if (this !is FileBasedStorage) {
        // upsource
        return@getOrCreateStorage
      }

      setFile(virtualFile, if (isMacroAdded) null else path)
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

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.components.*
import com.intellij.openapi.components.impl.stores.ModuleStore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
class NonPersistentModuleStore internal constructor() : ModuleStore {
  override val storageManager: StateStorageManager
    get() = NonPersistentStateStorageManager

  override val isStoreInitialized: Boolean
    get() = true
  override fun setPath(path: Path) {
  }

  override fun setPath(path: Path, virtualFile: VirtualFile?, isNew: Boolean) {
  }

  override fun initComponent(component: Any, serviceDescriptor: ServiceDescriptor?, pluginId: PluginId) {
  }

  override fun unloadComponent(component: Any) {}

  override fun initPersistencePlainComponent(component: Any, key: String, pluginId: PluginId) {
  }

  override fun reloadStates(componentNames: Set<String>) {}

  override fun reloadState(componentClass: Class<out PersistentStateComponent<*>>) {
  }

  override fun isReloadPossible(componentNames: Set<String>): Boolean = true

  override suspend fun save(forceSavingAllSettings: Boolean) {}

  override fun saveComponent(component: PersistentStateComponent<*>) {
  }

  override fun removeComponent(name: String) {
  }

  override fun clearCaches() {
  }

  override fun release() {
  }
}

private object NonPersistentStateStorageManager : StateStorageManager {
  override val componentManager: ComponentManager?
    get() = null

  override fun getStateStorage(storageSpec: Storage): StateStorage = NonPersistentStateStorage

  override fun addStreamProvider(provider: StreamProvider, first: Boolean) {}

  override fun removeStreamProvider(aClass: Class<out StreamProvider>) {}

  override fun getOldStorage(component: Any, componentName: String, operation: StateStorageOperation): StateStorage? = null

  override fun expandMacro(collapsedPath: String): Path = Path.of(collapsedPath)

  override fun collapseMacro(path: String): String = path

  override val streamProvider: StreamProvider
    get() = DummyStreamProvider
}

private object NonPersistentStateStorage : StateStorage {
  override fun <T : Any> getState(
    component: Any?,
    componentName: String,
    pluginId: PluginId,
    stateClass: Class<T>,
    mergeInto: T?,
    reload: Boolean,
  ): T? = null

  override fun createSaveSessionProducer(): SaveSessionProducer? = null

  override fun analyzeExternalChangesAndUpdateIfNeeded(componentNames: MutableSet<in String>) {}
}
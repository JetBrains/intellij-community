// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.components.impl.ModulePathMacroManager
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.module.Module
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class DefaultModuleStoreFactory : ModuleStoreFactory {
  override fun createModuleStore(module: Module): IComponentStore {
    val pathMacroManager = ModulePathMacroManager(module)
    return createDefaultStore(module, pathMacroManager)
  }

  companion object {
    fun createDefaultStore(module: Module, pathMacroManager: PathMacroManager): IComponentStore {
      return ModuleStoreImpl(module, pathMacroManager)
    }

    fun createNonPersistentStore(module: Module): IComponentStore {
      return NonPersistentModuleStore()
    }
  }
}
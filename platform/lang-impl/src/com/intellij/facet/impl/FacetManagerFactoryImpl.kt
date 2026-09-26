// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.facet.impl

import com.intellij.facet.FacetManager
import com.intellij.facet.FacetManagerFactory
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Disposer
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.workspaceModel.ide.impl.legacyBridge.facet.FacetManagerBridge
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

@ApiStatus.Internal
class FacetManagerFactoryImpl : FacetManagerFactory {
  private val facetManagerInstances = ConcurrentHashMap<Module, FacetManager>()

  // must be used only during project init
  fun getAllFacets(): Collection<FacetManager> {
    return facetManagerInstances.values
  }

  override fun getFacetManager(module: Module): FacetManager {
    if (module.isDisposed) {
      val exception = AlreadyDisposedException("Module is disposed: ${module.name}")
      thisLogger().error(exception)
      throw exception
    }
    return facetManagerInstances.computeIfAbsent(module) {
      Disposer.register(module) {
        facetManagerInstances.remove(module)
      }
      FacetManagerBridge(module)
    }
  }
}
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.facet.impl

import com.intellij.facet.FacetManager
import com.intellij.facet.FacetManagerFactory
import com.intellij.openapi.module.Module
import com.intellij.workspaceModel.ide.impl.legacyBridge.facet.FacetManagerBridge
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

@ApiStatus.Internal
internal class FacetManagerFactoryImpl(): FacetManagerFactory {
  private val facetManagerInstances = ConcurrentHashMap<Module, FacetManager>()

  override fun getFacetManager(module: Module): FacetManager {
    return facetManagerInstances.computeIfAbsent(module) { FacetManagerBridge(module) }
  }
}
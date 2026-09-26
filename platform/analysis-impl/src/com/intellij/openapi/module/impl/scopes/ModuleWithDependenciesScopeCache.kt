// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl.scopes

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Level.PROJECT)
class ModuleWithDependenciesScopeCache {
  private val cachedScopes: Cache<Pair<Module, Int>, ModuleWithDependenciesScope> = Caffeine.newBuilder().apply {
    val limit = Registry.get("module.with.dependencies.scope.cache.max.total.entries.count").asInteger()
    if (limit > -1) {
      maximumWeight(limit.toLong()) // default is 300k. Ultimate project only needs 40k
      weigher { _: Pair<Module, Int>, value: ModuleWithDependenciesScope ->
        value.rootContainer.size
      }
    }
  }.build()

  fun getCachedScope(module: Module, @ScopeConstant options: Int): GlobalSearchScope {
    return cachedScopes.get(Pair(module, options)) { ModuleWithDependenciesScope(module, options) }
  }

  fun clear() {
    cachedScopes.invalidateAll()
  }
}

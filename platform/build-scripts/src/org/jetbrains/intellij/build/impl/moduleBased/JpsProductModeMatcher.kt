// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.moduleBased

import com.intellij.platform.runtime.product.ProductMode
import com.intellij.platform.runtime.product.impl.ProductModeLoadingRules
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule

/**
 * This is an equivalent implementation of [com.intellij.platform.runtime.product.impl.ProductModeMatcher] based on JPS model instead of the runtime module repository.
 */
internal class JpsProductModeMatcher(productMode: ProductMode) {
  private val incompatibleRootModule = ProductModeLoadingRules.getIncompatibleRootModules(productMode).map { it.stringId }
  private val cache: MutableMap<JpsModule, Boolean> = mutableMapOf()
  
  fun matches(module: JpsModule): Boolean {
    val cached = cache[module]
    if (cached != null) return cached
    if (incompatibleRootModule.contains(module.name)) {
      cache[module] = false
      return false
    }
    
    cache[module] = true //this is needed to prevent StackOverflowError in the case of circular dependencies
    var matches = true
    JpsJavaExtensionService.dependencies(module).productionOnly().runtimeOnly().processModules { 
      matches = matches && matches(it)
    }
    cache[module] = matches
    return matches
  }
}

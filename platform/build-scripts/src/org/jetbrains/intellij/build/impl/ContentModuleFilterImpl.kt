// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.platform.runtime.product.ProductMode
import org.jetbrains.intellij.build.ContentModuleFilter
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.impl.moduleBased.JpsProductModeMatcher
import org.jetbrains.jps.model.JpsProject

/**
 * An instance of [ContentModuleFilter] which excludes modules not compatible with the given [ProductMode] from the platform part and bundled plugins.
 */
internal class ContentModuleByProductModeFilter(
  private val project: JpsProject,
  bundledPluginModules: List<String>,
  private val productMode: ProductMode
) : ContentModuleFilter {
  
  private val productModeMatcher = JpsProductModeMatcher(productMode)
  private val bundledPluginMainModules = bundledPluginModules.toSet()

  override fun isOptionalModuleIncluded(moduleName: String, pluginMainModuleName: String?): Boolean {
    if (pluginMainModuleName != null && pluginMainModuleName !in bundledPluginMainModules) {
      return true
    }
    val module = project.findModuleByName(moduleName) ?: return true
    return productModeMatcher.matches(module)
  }

  override fun toString(): String {
    return "ContentModuleByProductModeFilter{productMode=${productMode.id}}"
  }
}

internal object IncludeAllContentModuleFilter : ContentModuleFilter {
  override fun isOptionalModuleIncluded(moduleName: String, pluginMainModuleName: String?): Boolean = true
  
  override fun toString(): String = "IncludeAllContentModuleFilter"
}

internal class SkipUnresolvedOptionalContentModuleFilter(private val outputProvider: ModuleOutputProvider) : ContentModuleFilter {
  override fun isOptionalModuleIncluded(moduleName: String, pluginMainModuleName: String?): Boolean {
    return outputProvider.findModule(moduleName) != null
  }
  
  override fun toString(): String = "SkipUnresolvedOptionalContentModuleFilter"
}
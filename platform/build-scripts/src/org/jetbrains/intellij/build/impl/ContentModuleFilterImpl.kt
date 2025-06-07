// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.platform.runtime.product.ProductMode
import com.intellij.platform.runtime.product.impl.ProductModeMatcher
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.RuntimeModuleRepository
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.ContentModuleFilter

/**
 * An instance of [ContentModuleFilter] which excludes modules not compatible with the given [ProductMode] from the platform part and bundled plugins.
 */
internal class ContentModuleByProductModeFilter(
  private val moduleRepository: RuntimeModuleRepository, 
  bundledPluginModules: List<String>, 
  private val productMode: ProductMode
) : ContentModuleFilter {
  
  private val productModeMatcher = ProductModeMatcher(productMode)
  private val bundledPluginMainModules = bundledPluginModules.toSet()

  override fun isOptionalModuleIncluded(moduleName: String, pluginMainModuleName: String?): Boolean {
    if (pluginMainModuleName != null && pluginMainModuleName !in bundledPluginMainModules) {
      return true
    }
    val moduleDescriptor = moduleRepository.getModule(RuntimeModuleId.module(moduleName))
    return productModeMatcher.matches(moduleDescriptor)
  }

  override fun toString(): String {
    return "ContentModuleByProductModeFilter{productMode=${productMode.id}}"
  }
}

internal object IncludeAllContentModuleFilter : ContentModuleFilter {
  override fun isOptionalModuleIncluded(moduleName: String, pluginMainModuleName: String?): Boolean = true
  
  override fun toString(): String = "IncludeAllContentModuleFilter"
}

internal class SkipUnresolvedOptionalContentModuleFilter(private val context: BuildContext) : ContentModuleFilter {
  override fun isOptionalModuleIncluded(moduleName: String, pluginMainModuleName: String?): Boolean {
    return context.findModule(moduleName) != null;
  }
  
  override fun toString(): String = "SkipUnresolvedOptionalContentModuleFilter"
}
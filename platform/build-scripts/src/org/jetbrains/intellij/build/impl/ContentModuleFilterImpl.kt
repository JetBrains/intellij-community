// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.platform.productMode.ProductMode
import org.jetbrains.intellij.build.ContentModuleFilter
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.impl.moduleBased.JpsProductModeMatcher
import org.jetbrains.jps.model.JpsProject

/**
 * The filter a product applies to an optional `<module/>` of the platform or of a bundled plugin.
 *
 * One body, two callers, so the two cannot drift: [BuildContextImpl] asks during an assembly, and the
 * dev-distribution descriptor plan asks during generation. The plan carries the survivors, which is what lets a
 * produced descriptor be written by an action that loads no project model.
 *
 * [bundledPluginModules] is a lambda because the [ProductMode.MONOLITH] branches never ask for it, and computing the
 * bundled list is not free.
 */
fun createContentModuleFilter(
  project: JpsProject,
  productProperties: ProductProperties,
  outputProvider: ModuleOutputProvider,
  bundledPluginModules: () -> List<String>,
): ContentModuleFilter {
  if (productProperties.productMode == ProductMode.MONOLITH) {
    if (productProperties.productLayout.skipUnresolvedContentModules) {
      return SkipUnresolvedOptionalContentModuleFilter(outputProvider)
    }
    return IncludeAllContentModuleFilter
  }
  return ContentModuleByProductModeFilter(
    project = project,
    bundledPluginModules = bundledPluginModules().toSet(),
    productMode = productProperties.productMode,
  )
}

/**
 * The filter of [productMode] over every module of [project], whatever plugin holds it.
 *
 * The dev-distribution generator states the modules each mode refuses as a fact of the plugin, once per plugin and not
 * per product. So it asks for every mode a split product uses, and no plugin is bypassed as "not bundled".
 */
fun createProductModeContentModuleFilter(project: JpsProject, productMode: ProductMode): ContentModuleFilter {
  return ContentModuleByProductModeFilter(project = project, bundledPluginModules = null, productMode = productMode)
}

/**
 * An instance of [ContentModuleFilter] which excludes modules not compatible with the given [ProductMode] from the platform part and bundled plugins.
 *
 * [bundledPluginModules] names the plugins the filter applies to. A plugin outside the set is not filtered. `null` applies
 * the filter to every plugin.
 */
internal class ContentModuleByProductModeFilter(
  private val project: JpsProject,
  private val bundledPluginModules: Set<String>?,
  private val productMode: ProductMode
) : ContentModuleFilter {

  private val productModeMatcher by lazy { JpsProductModeMatcher(productMode) }

  override fun isOptionalModuleIncluded(moduleName: String, pluginMainModuleName: String?): Boolean {
    if (pluginMainModuleName != null && bundledPluginModules != null && !bundledPluginModules.contains(pluginMainModuleName)) {
      return true
    }
    val module = project.findModuleByName(moduleName) ?: return true
    return productModeMatcher.matches(module)
  }

  /** The run time excludes a refused plugin module by the same rule, so the descriptor keeps it and only the jar goes. */
  override fun keepsRefusedModuleInDescriptor(pluginMainModuleName: String?): Boolean = pluginMainModuleName != null

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
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.GraphScope
import com.intellij.platform.pluginGraph.PluginNode
import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRuleValue

/**
 * Collect content module dependencies from graph edges.
 *
 * Production plugins use production edges only. Test plugins include production edges
 * plus test edges to cover explicit XML deps and TEST-scope JPS deps.
 */
internal fun GraphScope.collectContentModuleDeps(
  contentModules: Set<ContentModuleName>,
  isTestPlugin: Boolean,
): Map<ContentModuleName, Set<ContentModuleName>> {
  val contentModuleDeps = LinkedHashMap<ContentModuleName, Set<ContentModuleName>>()
  for (moduleName in contentModules) {
    val moduleNode = contentModule(moduleName) ?: continue
    val deps = LinkedHashSet<ContentModuleName>()
    moduleNode.dependsOn { dep -> deps.add(dep.contentName()) }
    if (isTestPlugin) {
      moduleNode.dependsOnTest { dep -> deps.add(dep.contentName()) }
    }
    contentModuleDeps[moduleName] = deps
  }
  return contentModuleDeps
}

internal data class PluginContentSnapshot(
  val productionContentModules: LinkedHashSet<ContentModuleName>,
  val testContentModules: LinkedHashSet<ContentModuleName>,
  val allContentModules: LinkedHashSet<ContentModuleName>,
  val loadingModes: LinkedHashMap<ContentModuleName, ModuleLoadingRuleValue?>,
  val bundlingProducts: LinkedHashSet<String>,
) {
  fun contentModulesForValidation(isTestPlugin: Boolean): Set<ContentModuleName> {
    return if (isTestPlugin) allContentModules else productionContentModules
  }
}

internal fun GraphScope.collectPluginContentSnapshot(plugin: PluginNode): PluginContentSnapshot {
  val productionContentModules = LinkedHashSet<ContentModuleName>()
  val testContentModules = LinkedHashSet<ContentModuleName>()
  val allContentModules = LinkedHashSet<ContentModuleName>()
  val loadingModes = LinkedHashMap<ContentModuleName, ModuleLoadingRuleValue?>()
  val bundlingProducts = LinkedHashSet<String>()

  plugin.containsContent { module, loading ->
    val moduleName = module.contentName()
    productionContentModules.add(moduleName)
    allContentModules.add(moduleName)
    loadingModes[moduleName] = loading
  }
  plugin.containsContentTest { module, loading ->
    val moduleName = module.contentName()
    testContentModules.add(moduleName)
    allContentModules.add(moduleName)
    loadingModes[moduleName] = loading
  }
  plugin.bundledByProducts { product ->
    bundlingProducts.add(product.name())
  }

  return PluginContentSnapshot(
    productionContentModules = productionContentModules,
    testContentModules = testContentModules,
    allContentModules = allContentModules,
    loadingModes = loadingModes,
    bundlingProducts = bundlingProducts,
  )
}

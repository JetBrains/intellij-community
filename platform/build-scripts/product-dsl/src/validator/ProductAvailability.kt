// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.validator

import androidx.collection.MutableIntSet
import androidx.collection.MutableObjectIntMap
import androidx.collection.ObjectIntMap
import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.ContentModuleNode
import com.intellij.platform.pluginGraph.GraphScope
import com.intellij.platform.pluginGraph.ProductNode

internal data class ProductModules(
  @JvmField val modulesToValidate: List<ContentModuleNode>,
  @JvmField val duplicateModules: ObjectIntMap<ContentModuleName>,
)

internal fun GraphScope.collectProductModules(product: ProductNode): ProductModules {
  val modulesToValidate = ArrayList<ContentModuleNode>()
  val seenModuleIds = MutableIntSet()
  val duplicateModules = MutableObjectIntMap<ContentModuleName>()

  fun addModule(module: ContentModuleNode) {
    if (seenModuleIds.add(module.id)) {
      modulesToValidate.add(module)
    }
    else {
      val name = module.contentName()
      duplicateModules.put(name, duplicateModules.getOrDefault(name, 1) + 1)
    }
  }

  // Product modules = module sets + direct product content
  product.includesModuleSet { moduleSet ->
    moduleSet.modulesRecursive { module -> addModule(module) }
  }
  product.containsContent { module, _ -> addModule(module) }

  return ProductModules(
    modulesToValidate = modulesToValidate,
    duplicateModules = duplicateModules,
  )
}

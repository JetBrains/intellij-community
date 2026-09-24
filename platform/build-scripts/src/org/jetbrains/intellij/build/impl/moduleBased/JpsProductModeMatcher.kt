// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.moduleBased

import com.intellij.platform.runtime.product.ProductMode
import com.intellij.platform.runtime.product.impl.ProductModeLoadingRules
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.jps.model.module.JpsModule

/**
 * This is an equivalent implementation of product-mode matching based on JPS model instead of the runtime module repository.
 * [ModuleRootMatcher] holds the walk, over the incompatible root modules of [productMode].
 */
@VisibleForTesting
class JpsProductModeMatcher(productMode: ProductMode) {
  private val matcher = ModuleRootMatcher(ProductModeLoadingRules.getIncompatibleRootModules(productMode).mapTo(HashSet()) { it.name })

  fun matches(module: JpsModule): Boolean = matcher.matches(module)
}

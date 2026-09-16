// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework.pluginModel

import org.jetbrains.annotations.ApiStatus.Internal

/**
 * The plugins that each variant of [PluginDependenciesValidator] must leave out.
 *
 * [PluginDependenciesValidator] loads the bundled plugins and the plugins to publish as one set.
 * Two of them may conflict, so one variant cannot validate them all.
 * A product states its own variants in `ProductModulesLayout.pluginExclusionVariants`.
 */
@Internal
class PluginExclusionSubsets(
  /** The plugin ids that one variant excludes. Holds at least one element. */
  @JvmField val subsets: List<Set<String>>,
  /** The plugin that no variant validates, by its main module or by its id, and the reason. */
  @JvmField val uncoveredPlugins: Map<String, String>,
)

/** One variant, and no exclusion. */
@Internal
val singleValidationVariant: PluginExclusionSubsets = PluginExclusionSubsets(subsets = listOf(emptySet()), uncoveredPlugins = emptyMap())

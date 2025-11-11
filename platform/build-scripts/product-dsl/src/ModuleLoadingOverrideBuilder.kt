// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package org.jetbrains.intellij.build.productLayout

import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRule
import kotlinx.serialization.Serializable

/**
 * Wrapper for a module set with optional loading overrides.
 * When overrides are present, the module set will be inlined in the product XML
 * instead of being referenced via xi:include.
 */
@Serializable
data class ModuleSetWithOverrides(
  @JvmField val moduleSet: ModuleSet,
  @JvmField val loadingOverrides: Map<String, ModuleLoadingRule> = emptyMap(),
) {
  val hasOverrides: Boolean
    get() = loadingOverrides.isNotEmpty()
}

/**
 * DSL builder for module loading overrides within a module set.
 */
@ProductDslMarker
class ModuleLoadingOverrideBuilder {
  private val overrides = HashMap<String, ModuleLoadingRule>()

  /**
   * Override a module in this module set to be loaded as embedded (loading="embedded").
   */
  fun overrideAsEmbedded(moduleName: String) {
    overrides.put(moduleName, ModuleLoadingRule.EMBEDDED)
  }

  /**
   * Override a module in this module set to be loaded as required (loading="required").
   */
  fun overrideAsRequired(moduleName: String) {
    overrides.put(moduleName, ModuleLoadingRule.REQUIRED)
  }

  /**
   * Set custom loading rule for modules.
   */
  fun loading(rule: ModuleLoadingRule, vararg moduleNames: String) {
    for (name in moduleNames) {
      overrides.put(name, rule)
    }
  }

  @PublishedApi
  internal fun build(): Map<String, ModuleLoadingRule> = overrides.toMap()
}
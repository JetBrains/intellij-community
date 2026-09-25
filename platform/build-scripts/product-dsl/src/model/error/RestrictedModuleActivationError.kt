// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.model.error

import com.intellij.platform.pluginGraph.ContentModuleName
import org.jetbrains.intellij.build.productLayout.stats.AnsiStyle

/** Reports an active restricted module that no activation allows, or a required module that remains inactive. */
internal data class RestrictedModuleActivationError(
  override val context: String,
  @JvmField val productModeId: String,
  @JvmField val productModeExcludedModules: Set<ContentModuleName>,
  @JvmField val unexpectedModules: Map<ContentModuleName, List<String>>,
  @JvmField val missingModules: Map<ContentModuleName, String>,
) : ValidationError {
  override val category: ErrorCategory get() = ErrorCategory.RESTRICTED_MODULE_ACTIVATION
  override val ruleName: String get() = "RestrictedModuleActivationValidation"

  override fun format(s: AnsiStyle): String {
    return buildString {
      appendLine("${s.red}${s.bold}Product '$context' violates the restricted module activation rule.${s.reset}")
      for ((module, path) in unexpectedModules.entries.sortedBy { it.key.value }) {
        appendLine("  Unexpected active restricted module: ${module.value}")
        appendLine("    Activation: ${path.joinToString(" -> ")}")
      }
      if (unexpectedModules.isNotEmpty()) {
        appendLine("  No module activation of this product or of its plugins allows these modules.")
        if (productModeExcludedModules.isNotEmpty()) {
          val excluded = productModeExcludedModules.sortedBy { it.value }.joinToString { it.value }
          appendLine("  The '$productModeId' product mode excludes: $excluded.")
          appendLine("  A consumer that must not load in this mode needs a dependency on one of these modules.")
        }
        appendLine("  In a test plugin, declare an unused restricted test framework with on-demand loading.")
      }
      for ((module, reason) in missingModules.entries.sortedBy { it.key.value }) {
        appendLine("  Required module is inactive: ${module.value}")
        appendLine("    $reason")
      }
      appendLine("${s.gray}[Rule: $ruleName]${s.reset}")
    }
  }
}

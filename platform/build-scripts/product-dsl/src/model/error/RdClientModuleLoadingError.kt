// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.model.error

import com.intellij.platform.pluginGraph.ContentModuleName
import org.jetbrains.intellij.build.productLayout.stats.AnsiStyle

/** Reports unexpected RD client activation or required modules that remain inactive. */
internal data class RdClientModuleLoadingError(
  override val context: String,
  @JvmField val unexpectedModules: Map<ContentModuleName, List<String>>,
  @JvmField val missingModules: Map<ContentModuleName, String>,
) : ValidationError {
  override val category: ErrorCategory get() = ErrorCategory.RD_CLIENT_MODULE_LOADING
  override val ruleName: String get() = "RdClientModuleLoadingValidation"

  override fun format(s: AnsiStyle): String {
    return buildString {
      appendLine("${s.red}${s.bold}Product '$context' violates the RD client loading rule.${s.reset}")
      for ((module, path) in unexpectedModules.entries.sortedBy { it.key.value }) {
        appendLine("  Unexpected active module: ${module.value}")
        appendLine("    Activation: ${path.joinToString(" -> ")}")
      }
      if (unexpectedModules.isNotEmpty()) {
        appendLine("  You probably created a .frontend.split module without a dependency on intellij.platform.frontend.split.")
        appendLine("  This makes your module eligible to load in a monolith IDE and loads a forbidden `intellij.rd.client` module.")
      }
      for ((module, reason) in missingModules.entries.sortedBy { it.key.value }) {
        appendLine("  Required module is inactive: ${module.value}")
        appendLine("    $reason")
      }
      appendLine("${s.gray}[Rule: $ruleName]${s.reset}")
    }
  }
}

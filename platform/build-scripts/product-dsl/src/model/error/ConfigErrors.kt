// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.model.error

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginId
import com.intellij.platform.pluginGraph.TargetName
import org.jetbrains.intellij.build.productLayout.stats.AnsiStyle
import java.nio.file.Path

/**
 * Error when a product tries to bundle a plugin that wasn't extracted in Phase 1.
 *
 * This usually means:
 * - The module doesn't have META-INF/plugin.xml
 * - The module name is misspelled in the product spec
 * - The module isn't included in the build
 */
data class MissingPluginInGraphError(
  override val context: String,
  /** Product name that tried to bundle the plugin */
  @JvmField val productName: String,
  /** Plugin module name that wasn't found */
  val pluginName: TargetName,
  /** Whether this was a test plugin */
  @JvmField val isTestPlugin: Boolean,
  override val ruleName: String = "PluginGraphValidation",
) : ValidationError {
  override val category: ErrorCategory get() = ErrorCategory.MISSING_PLUGIN_IN_GRAPH

  override fun format(s: AnsiStyle): String = buildString {
    val pluginType = if (isTestPlugin) "test plugin" else "plugin"
    appendLine("${s.red}${s.bold}Product '$productName' bundles $pluginType '${pluginName.value}' which wasn't found${s.reset}")
    appendLine()
    appendLine("  ${s.yellow}The plugin must be extracted in Phase 1 before it can be bundled.${s.reset}")
    appendLine("  This usually means the module doesn't have META-INF/plugin.xml.")
    appendLine()
    appendLine("${s.blue}Fix:${s.reset} Either:")
    appendLine("  1. Add META-INF/plugin.xml to the module if it should be a plugin")
    appendLine("  2. Remove the module from bundledPlugins in the product spec if it shouldn't be a plugin")
    appendLine("  3. Check for typos in the module name")
    appendLine()
    appendLine("${s.gray}[Rule: $ruleName]${s.reset}")
    appendLine()
  }
}

/**
 * Error when a plugin.xml is missing <id> element.
 */
data class MissingPluginIdError(
  override val context: String,
  /** Plugin module name (target) */
  val pluginName: TargetName,
  /** Path to plugin.xml where <id> was missing */
  @JvmField val pluginXmlPath: Path,
  /** Plugin source label (BUNDLED, TEST, DSL_TEST, DISCOVERED) */
  @JvmField val pluginSource: String,
  override val ruleName: String = "PluginIdExtraction",
) : ValidationError {
  override val category: ErrorCategory get() = ErrorCategory.MISSING_PLUGIN_ID
  override val suppressionKey: String get() = suppressionKeyFor(pluginName)

  override fun format(s: AnsiStyle): String = buildString {
    appendLine("${s.red}${s.bold}Plugin '${pluginName.value}' has no <id> in plugin.xml${s.reset}")
    appendLine()
    appendLine("  ${s.red}*${s.reset} Path: ${s.bold}$pluginXmlPath${s.reset}")
    appendLine("  ${s.red}*${s.reset} Source: ${s.bold}$pluginSource${s.reset}")
    appendLine()
    appendLine("${s.blue}Fix:${s.reset} Add a <id>...</id> to the plugin.xml,")
    appendLine("     or remove the module from bundled/test plugin lists if it shouldn't be a plugin.")
    appendLine()
    appendLine("${s.blue}To suppress:${s.reset} Add to plugins in suppressions.json:")
    appendLine("  ${s.gray}\"${pluginName.value}\": {\"allowMissingPluginId\": true}${s.reset}")
    appendLine()
    appendLine("${s.gray}[Rule: $ruleName]${s.reset}")
    appendLine()
  }

  companion object {
    fun suppressionKeyFor(pluginName: TargetName): String = "missing-plugin-id:${pluginName.value}"
  }
}

/**
 * Error when a DSL-defined test plugin id is declared more than once.
 */
data class DuplicateDslTestPluginIdError(
  override val context: String,
  val pluginId: PluginId,
  /** Product names and their occurrence counts */
  @JvmField val productCounts: Map<String, Int>,
  override val ruleName: String = "DslTestPluginIdUniqueness",
) : ValidationError {
  override val category: ErrorCategory get() = ErrorCategory.DUPLICATE_DSL_TEST_PLUGIN_ID

  override fun format(s: AnsiStyle): String = buildString {
    appendLine("${s.red}${s.bold}DSL test plugin id '${pluginId.value}' is declared multiple times${s.reset}")
    appendLine()
    appendLine("  ${s.yellow}Products:${s.reset}")
    for ((productName, count) in productCounts.entries.sortedBy { it.key }) {
      val countSuffix = if (count > 1) " (x$count)" else ""
      appendLine("    ${s.red}*${s.reset} ${s.bold}$productName${s.reset}$countSuffix")
    }
    appendLine()
    appendLine("${s.blue}Fix:${s.reset} Ensure each DSL test plugin id is declared only once (unique across products).")
    appendLine()
    appendLine("${s.gray}[Rule: $ruleName]${s.reset}")
    appendLine()
  }
}

data class XIncludeResolutionError(
  override val context: String,
  /** Plugin module name where xi:include was found */
  @JvmField val pluginName: String,
  /** xi:include path that failed to resolve */
  @JvmField val xIncludePath: String,
  /** Internal debug info (search details) */
  @JvmField val debugInfo: String,
  override val ruleName: String = "XIncludeResolution",
) : ValidationError {
  override val category: ErrorCategory get() = ErrorCategory.XI_INCLUDE_RESOLUTION

  override fun format(s: AnsiStyle): String = buildString {
    appendLine("${s.red}${s.bold}Failed to resolve xi:include in plugin $pluginName${s.reset}")
    appendLine()
    appendLine("  ${s.red}*${s.reset} Path: ${s.bold}$xIncludePath${s.reset}")
    appendLine("  ${s.gray}Debug: $debugInfo${s.reset}")
    appendLine()
    appendLine("${s.blue}Fix: If this file comes from an external library, add the path to 'skipXIncludePaths' in ModuleSetGenerationConfig${s.reset}")
    appendLine()
    appendLine("${s.gray}[Rule: $ruleName]${s.reset}")
    appendLine()
  }
}

/**
 * Error when suppression config contains keys that don't match real modules/plugins.
 */
data class InvalidSuppressionConfigKeyError(
  override val context: String,
  /** Keys in contentModules that don't match any content module */
  @JvmField val invalidContentModuleKeys: Set<ContentModuleName>,
  /** Keys in plugins that don't match any plugin */
  @JvmField val invalidPluginKeys: Set<ContentModuleName>,
  override val ruleName: String = "SuppressionConfigValidation",
) : ValidationError {
  override val category: ErrorCategory get() = ErrorCategory.INVALID_SUPPRESSION_KEY

  override fun format(s: AnsiStyle): String = buildString {
    appendLine("${s.red}${s.bold}Suppression config contains invalid keys${s.reset}")
    appendLine()
    if (invalidContentModuleKeys.isNotEmpty()) {
      appendLine("  ${s.yellow}Invalid contentModules keys (not actual content modules):${s.reset}")
      for (key in invalidContentModuleKeys.map { it.value }.sorted()) {
        appendLine("    ${s.red}*${s.reset} ${s.bold}$key${s.reset}")
      }
      appendLine()
    }
    if (invalidPluginKeys.isNotEmpty()) {
      appendLine("  ${s.yellow}Invalid plugins keys (not actual plugin modules):${s.reset}")
      for (key in invalidPluginKeys.map { it.value }.sorted()) {
        appendLine("    ${s.red}*${s.reset} ${s.bold}$key${s.reset}")
      }
      appendLine()
    }
    appendLine("${s.blue}Fix:${s.reset} Remove stale keys or fix typos in suppressions.json")
    appendLine()
    appendLine("${s.gray}[Rule: $ruleName]${s.reset}")
    appendLine()
  }
}

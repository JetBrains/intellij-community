// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "GrazieInspection")

package org.jetbrains.intellij.build.productLayout.generator

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.productLayout.config.ContentModuleSuppression
import org.jetbrains.intellij.build.productLayout.config.PluginSuppression
import org.jetbrains.intellij.build.productLayout.config.SuppressionConfig
import org.jetbrains.intellij.build.productLayout.debug
import org.jetbrains.intellij.build.productLayout.model.error.MissingPluginIdError
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.DataSlot
import org.jetbrains.intellij.build.productLayout.pipeline.ErrorSlot
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode
import org.jetbrains.intellij.build.productLayout.pipeline.Slots
import org.jetbrains.intellij.build.productLayout.pipeline.SuppressionConfigOutput
import org.jetbrains.intellij.build.productLayout.stats.SuppressionType
import org.jetbrains.intellij.build.productLayout.stats.SuppressionUsage
import java.nio.file.Files
import java.util.TreeMap
import java.util.TreeSet

/**
 * Generator for the suppression config JSON file (`suppressions.json`).
 *
 * ## Purpose
 *
 * Provides a **single source of truth** for dependency suppressions in JSON format.
 *
 * ## Unified Architecture
 *
 * This generator uses [SuppressionUsage] as the single source of truth for what was suppressed.
 * All generators and validators emit SuppressionUsage records when they apply suppressions.
 * This generator simply aggregates these records into the suppressions.json format.
 *
 * **Types of suppressions (from SuppressionType):**
 * - `MODULE_DEP` → `contentModules[].suppressModules`
 * - `PLUGIN_DEP` → `contentModules[].suppressPlugins`
 * - `PLUGIN_XML_MODULE` → `plugins[].suppressModules`
 * - `PLUGIN_XML_PLUGIN` → `plugins[].suppressPlugins`
 * - `LIBRARY_REPLACEMENT` → `contentModules[].suppressLibraries`
 * - `TEST_LIBRARY_SCOPE` → `contentModules[].suppressTestLibraryScope`
 *
 * ## Stale Detection
 *
 * A suppression in the existing config is **stale** if no corresponding [SuppressionUsage]
 * was emitted during generation. This means the JPS dependency no longer exists or has changed.
 *
 * ## Output
 *
 * `platform/buildScripts/suppressions.json` (canonical location for Ultimate builds)
 */
internal object SuppressionConfigGenerator : PipelineNode {
  override val id get() = NodeIds.SUPPRESSION_CONFIG
  override val produces: Set<DataSlot<*>> get() = setOf(Slots.SUPPRESSION_CONFIG)
  override val requires: Set<DataSlot<*>> get() = setOf(
    Slots.PRODUCT_MODULE_DEPS,
    Slots.CONTENT_MODULE,
    Slots.PLUGIN_XML,
    Slots.LIBRARY_SUPPRESSIONS,
    Slots.TEST_LIBRARY_SCOPE_SUPPRESSIONS,
  )

  override suspend fun execute(ctx: ComputeContext) {
    val model = ctx.model
    val existingConfig = model.suppressionConfig

    // Collect ALL suppression usages from all generators and validators
    val allUsages = ArrayList<SuppressionUsage>()

    // From content module dependency generator
    val contentModuleOutput = ctx.get(Slots.CONTENT_MODULE)
    for (file in contentModuleOutput.files) {
      allUsages.addAll(file.suppressionUsages)
    }

    // From product module dependency generator (module sets)
    val productModuleOutput = ctx.get(Slots.PRODUCT_MODULE_DEPS)
    for (file in productModuleOutput.files) {
      allUsages.addAll(file.suppressionUsages)
    }

    // From plugin.xml dependency generator
    val pluginXmlOutput = ctx.get(Slots.PLUGIN_XML)
    for (result in pluginXmlOutput.detailedResults) {
      allUsages.addAll(result.suppressionUsages)
    }

    // From validators
    allUsages.addAll(ctx.get(Slots.LIBRARY_SUPPRESSIONS))
    allUsages.addAll(ctx.get(Slots.TEST_LIBRARY_SCOPE_SUPPRESSIONS))

    // From DSL test plugin dependency traversal
    allUsages.addAll(model.dslTestPluginSuppressionUsages)

    // Extract scope from generator outputs - these are the modules processed in this run
    val processedContentModules = contentModuleOutput.files.mapTo(HashSet()) { it.contentModuleName }
    processedContentModules.addAll(productModuleOutput.files.map { it.contentModuleName })
    val processedPluginModules = pluginXmlOutput.detailedResults.mapTo(HashSet()) { it.pluginContentModuleName }

    // Build suppression config from usages (single source of truth - no preservation of stale entries)
    val (contentModules, plugins) = buildSuppressionsFromUsages(allUsages)

    // Handle suppressedErrors (error key suppressions)
    val contentModuleErrors = ctx.get(ErrorSlot(NodeIds.CONTENT_MODULE_DEPS))
    val pluginXmlErrors = ctx.get(ErrorSlot(NodeIds.PLUGIN_XML_DEPS))
    val generatedErrorKeys = collectGeneratedErrorKeys(contentModuleErrors, pluginXmlErrors)
    val (filteredSuppressedErrors, suppressedErrorsStaleCount) = filterSuppressedErrors(
      existing = existingConfig.suppressedErrors,
      generatedErrorKeys = generatedErrorKeys,
    )

    val missingPluginIdPlugins = collectMissingPluginIdPlugins(pluginXmlErrors)
    val mergedPlugins = mergeMissingPluginIdSuppressions(
      basePlugins = plugins,
      existingConfig = existingConfig,
      missingPluginIdPlugins = missingPluginIdPlugins,
      processedPluginModules = processedPluginModules,
      updateSuppressions = model.updateSuppressions,
    )

    // Count stale suppressions (existing but no usage emitted) - only for modules in scope
    val staleCount = countStaleSuppressions(
      existingConfig,
      allUsages,
      processedContentModules,
      processedPluginModules,
      missingPluginIdPlugins,
    ) + suppressedErrorsStaleCount

    // Build new config
    val newConfig = SuppressionConfig(
      contentModules = contentModules,
      plugins = mergedPlugins,
      validationExceptions = existingConfig.validationExceptions,
      suppressedErrors = filteredSuppressedErrors,
    )

    // Load fresh config from disk for comparison
    val configPath = model.config.suppressionConfigPath
    val diskConfig = SuppressionConfig.load(configPath)
    val configModified = newConfig != diskConfig

    // Only update suppressions.json when explicitly requested via --update-suppressions flag
    if (configPath != null && model.updateSuppressions) {
      val newJsonContent = SuppressionConfig.serializeToString(newConfig)
      withContext(Dispatchers.IO) {
        Files.writeString(configPath, newJsonContent)
      }
    }

    val moduleCount = contentModules.size + plugins.size
    val suppressionCount = contentModules.values.sumOf {
      it.suppressModules.size + it.suppressPlugins.size + it.suppressLibraries.size + it.suppressTestLibraryScope.size
    } + plugins.values.sumOf { it.suppressModules.size + it.suppressPlugins.size }

    ctx.publish(Slots.SUPPRESSION_CONFIG, SuppressionConfigOutput(
      moduleCount = moduleCount,
      suppressionCount = suppressionCount,
      configModified = configModified,
      staleCount = staleCount,
    ))
  }
}

/**
 * Builds suppression config maps from SuppressionUsage records.
 *
 * Groups usages by source module and type, then builds the appropriate suppression objects.
 */
private fun buildSuppressionsFromUsages(
  usages: List<SuppressionUsage>,
): Pair<Map<ContentModuleName, ContentModuleSuppression>, Map<ContentModuleName, PluginSuppression>> {
  // Group by (sourceModule, type)
  val byModuleAndType = usages.groupBy { it.sourceModule to it.type }

  // Build content module suppressions
  val contentModules = TreeMap<ContentModuleName, ContentModuleSuppression>()

  // Collect all modules that have any content module suppression type
  val contentModuleNames = usages
    .filter { it.type in CONTENT_MODULE_TYPES }
    .map { it.sourceModule }
    .toSet()

  for (moduleName in contentModuleNames) {
    val suppressModules = byModuleAndType.get(moduleName to SuppressionType.MODULE_DEP)
      ?.mapTo(TreeSet()) { ContentModuleName(it.suppressedDep) } ?: emptySet()
    val suppressPlugins = byModuleAndType.get(moduleName to SuppressionType.PLUGIN_DEP)
      ?.mapTo(TreeSet()) { PluginId(it.suppressedDep) } ?: emptySet()
    val suppressLibraries = byModuleAndType.get(moduleName to SuppressionType.LIBRARY_REPLACEMENT)
      ?.mapTo(TreeSet()) { it.suppressedDep } ?: emptySet()
    val suppressTestLibraryScope = byModuleAndType.get(moduleName to SuppressionType.TEST_LIBRARY_SCOPE)
      ?.mapTo(TreeSet()) { it.suppressedDep } ?: emptySet()

    if (suppressModules.isNotEmpty() || suppressPlugins.isNotEmpty() ||
        suppressLibraries.isNotEmpty() || suppressTestLibraryScope.isNotEmpty()) {
      contentModules.put(moduleName, ContentModuleSuppression(
        suppressModules = suppressModules,
        suppressPlugins = suppressPlugins,
        suppressLibraries = suppressLibraries,
        suppressTestLibraryScope = suppressTestLibraryScope,
      ))
    }
  }

  // Build plugin suppressions
  val plugins = TreeMap<ContentModuleName, PluginSuppression>()

  val pluginModuleNames = usages
    .filter { it.type in PLUGIN_TYPES }
    .map { it.sourceModule }
    .toSet()

  for (moduleName in pluginModuleNames) {
    val suppressModules = byModuleAndType.get(moduleName to SuppressionType.PLUGIN_XML_MODULE)
      ?.mapTo(TreeSet()) { ContentModuleName(it.suppressedDep) } ?: emptySet()
    val suppressPlugins = byModuleAndType.get(moduleName to SuppressionType.PLUGIN_XML_PLUGIN)
      ?.mapTo(TreeSet()) { PluginId(it.suppressedDep) } ?: emptySet()

    if (suppressModules.isNotEmpty() || suppressPlugins.isNotEmpty()) {
      plugins.put(moduleName, PluginSuppression(
        suppressModules = suppressModules,
        suppressPlugins = suppressPlugins,
      ))
    }
  }

  return contentModules to plugins
}

private val CONTENT_MODULE_TYPES = setOf(
  SuppressionType.MODULE_DEP,
  SuppressionType.PLUGIN_DEP,
  SuppressionType.LIBRARY_REPLACEMENT,
  SuppressionType.TEST_LIBRARY_SCOPE,
)

private val PLUGIN_TYPES = setOf(
  SuppressionType.PLUGIN_XML_MODULE,
  SuppressionType.PLUGIN_XML_PLUGIN,
)

/**
 * Counts stale suppressions in existing config.
 *
 * A suppression is stale if:
 * 1. The module WAS processed in this run (in scope)
 * 2. No corresponding [SuppressionUsage] was emitted
 *
 * Modules NOT in scope (belong to other products) are skipped - their suppressions are preserved.
 */
private fun countStaleSuppressions(
  existing: SuppressionConfig,
  usages: List<SuppressionUsage>,
  processedContentModules: Set<ContentModuleName>,
  processedPluginModules: Set<ContentModuleName>,
  missingPluginIdPlugins: Set<ContentModuleName>,
): Int {
  // Build set of (module, dep, type) tuples from usages for O(1) lookup
  val usageKeys = usages.mapTo(HashSet()) { Triple(it.sourceModule, it.suppressedDep, it.type) }

  var staleCount = 0
  val staleDetails = ArrayList<String>()

  // Check content module suppressions (only for modules in scope)
  for ((moduleName, suppression) in existing.contentModules) {
    if (moduleName !in processedContentModules) {
      debug("stale") { "SKIP out-of-scope contentModule: $moduleName" }
      continue  // Out of scope - skip
    }
    
    for (dep in suppression.suppressModules) {
      if (Triple(moduleName, dep.value, SuppressionType.MODULE_DEP) !in usageKeys) {
        staleCount++
        staleDetails.add("MODULE_DEP: $moduleName -> ${dep.value}")
      }
    }
    for (dep in suppression.suppressPlugins) {
      if (Triple(moduleName, dep.value, SuppressionType.PLUGIN_DEP) !in usageKeys) {
        staleCount++
        staleDetails.add("PLUGIN_DEP: $moduleName -> ${dep.value}")
      }
    }
    for (dep in suppression.suppressLibraries) {
      if (Triple(moduleName, dep, SuppressionType.LIBRARY_REPLACEMENT) !in usageKeys) {
        staleCount++
        staleDetails.add("LIBRARY_REPLACEMENT: $moduleName -> $dep")
      }
    }
    for (dep in suppression.suppressTestLibraryScope) {
      if (Triple(moduleName, dep, SuppressionType.TEST_LIBRARY_SCOPE) !in usageKeys) {
        staleCount++
        staleDetails.add("TEST_LIBRARY_SCOPE: $moduleName -> $dep")
      }
    }
  }

  // Check plugin suppressions (only for plugins in scope)
  for ((moduleName, suppression) in existing.plugins) {
    if (moduleName !in processedPluginModules) {
      debug("stale") { "SKIP out-of-scope plugin: $moduleName" }
      continue  // Out of scope - skip
    }
    
    for (dep in suppression.suppressModules) {
      if (Triple(moduleName, dep.value, SuppressionType.PLUGIN_XML_MODULE) !in usageKeys) {
        staleCount++
        staleDetails.add("PLUGIN_XML_MODULE: $moduleName -> ${dep.value}")
      }
    }
    for (dep in suppression.suppressPlugins) {
      if (Triple(moduleName, dep.value, SuppressionType.PLUGIN_XML_PLUGIN) !in usageKeys) {
        staleCount++
        staleDetails.add("PLUGIN_XML_PLUGIN: $moduleName -> ${dep.value}")
      }
    }

    if (suppression.allowMissingPluginId && moduleName !in missingPluginIdPlugins) {
      staleCount++
      staleDetails.add("ALLOW_MISSING_PLUGIN_ID: $moduleName")
    }
  }

  if (staleDetails.isNotEmpty()) {
    debug("stale") { "Stale suppressions ($staleCount):\n${staleDetails.joinToString("\n")}" }
  }

  return staleCount
}

/**
 * Extracts error keys from validation errors.
 */
private fun collectGeneratedErrorKeys(
  vararg errorLists: List<org.jetbrains.intellij.build.productLayout.model.error.ValidationError>,
): Set<String> {
  val allKeys = HashSet<String>()
  for (errors in errorLists) {
    for (error in errors) {
      if (error is org.jetbrains.intellij.build.productLayout.model.error.UnsuppressedPipelineError) {
        allKeys.add(error.errorKey)
      }
    }
  }
  return allKeys
}

/**
 * Filters suppressedErrors to remove stale entries.
 */
private fun filterSuppressedErrors(
  existing: Set<String>,
  generatedErrorKeys: Set<String>,
): Pair<Set<String>, Int> {
  val valid = HashSet<String>()
  var staleCount = 0

  for (errorKey in existing) {
    if (errorKey in generatedErrorKeys) {
      valid.add(errorKey)
    }
    else {
      staleCount++
    }
  }

  return valid to staleCount
}

private fun collectMissingPluginIdPlugins(
  errors: List<org.jetbrains.intellij.build.productLayout.model.error.ValidationError>,
): Set<ContentModuleName> {
  val result = HashSet<ContentModuleName>()
  for (error in errors) {
    if (error is MissingPluginIdError) {
      result.add(ContentModuleName(error.pluginName.value))
    }
  }
  return result
}

private fun mergeMissingPluginIdSuppressions(
  basePlugins: Map<ContentModuleName, PluginSuppression>,
  existingConfig: SuppressionConfig,
  missingPluginIdPlugins: Set<ContentModuleName>,
  processedPluginModules: Set<ContentModuleName>,
  updateSuppressions: Boolean,
): Map<ContentModuleName, PluginSuppression> {
  if (missingPluginIdPlugins.isEmpty() && existingConfig.plugins.none { it.value.allowMissingPluginId }) {
    return basePlugins
  }

  val result = TreeMap<ContentModuleName, PluginSuppression>()
  result.putAll(basePlugins)

  val allowMissing = HashSet<ContentModuleName>()
  for ((moduleName, suppression) in existingConfig.plugins) {
    if (suppression.allowMissingPluginId) {
      allowMissing.add(moduleName)
    }
  }

  if (updateSuppressions) {
    allowMissing.removeIf { it in processedPluginModules && it !in missingPluginIdPlugins }
    allowMissing.addAll(missingPluginIdPlugins)
  }

  for (moduleName in allowMissing) {
    val existing = result[moduleName]
    if (existing == null) {
      result[moduleName] = PluginSuppression(allowMissingPluginId = true)
    }
    else if (!existing.allowMissingPluginId) {
      result[moduleName] = existing.copy(allowMissingPluginId = true)
    }
  }

  return result
}

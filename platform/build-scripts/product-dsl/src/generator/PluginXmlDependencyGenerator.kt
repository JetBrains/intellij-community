// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// See docs/dependency_generation.md for dependency generation documentation
@file:Suppress("ReplaceGetOrSet", "GrazieStyle")

package org.jetbrains.intellij.build.productLayout.generator

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.DependencyClassification
import com.intellij.platform.pluginGraph.PluginGraph
import com.intellij.platform.pluginGraph.PluginId
import com.intellij.platform.pluginGraph.TargetName
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.intellij.build.productLayout.LIB_MODULE_PREFIX
import org.jetbrains.intellij.build.productLayout.config.SuppressionConfig
import org.jetbrains.intellij.build.productLayout.debug
import org.jetbrains.intellij.build.productLayout.dependency.PluginContentProvider
import org.jetbrains.intellij.build.productLayout.discovery.PluginContentInfo
import org.jetbrains.intellij.build.productLayout.discovery.PluginSource
import org.jetbrains.intellij.build.productLayout.model.error.MissingPluginIdError
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.DataSlot
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode
import org.jetbrains.intellij.build.productLayout.pipeline.PluginXmlOutput
import org.jetbrains.intellij.build.productLayout.pipeline.Slots
import org.jetbrains.intellij.build.productLayout.stats.PluginDependencyFileResult
import org.jetbrains.intellij.build.productLayout.stats.PluginXmlFileResult
import org.jetbrains.intellij.build.productLayout.stats.SuppressionType
import org.jetbrains.intellij.build.productLayout.stats.SuppressionUsage
import org.jetbrains.intellij.build.productLayout.util.FileUpdateStrategy
import org.jetbrains.intellij.build.productLayout.xml.LegacyMigrationResult
import org.jetbrains.intellij.build.productLayout.xml.updateXmlDependencies

/**
 * Generator for plugin.xml dependency XML files.
 *
 * Generates `<dependencies>` sections for plugin.xml files. Both module and plugin
 * dependencies are derived from the plugin graph (JPS deps + plugin metadata):
 * - JPS target dep with {moduleName}.xml → `<module name="..."/>` dependency
 * - JPS target dep with META-INF/plugin.xml → `<plugin id="..."/>` dependency
 *
 * Only production-runtime scopes (COMPILE/RUNTIME) are considered.
 *
 * **Input:** PluginGraph nodes with a main target (real plugins; placeholder plugin-id nodes are skipped).
 * DSL-defined plugins are generated from Kotlin specs and are skipped here.
 * **Output:** Updated plugin.xml files with `<dependencies>` sections
 *
 * **Publishes:** [Slots.PLUGIN_XML] for downstream validation
 *
 * **No dependencies** - can run immediately (level 0).
 */
internal object PluginXmlDependencyGenerator : PipelineNode {
  override val id get() = NodeIds.PLUGIN_XML_DEPS
  override val produces: Set<DataSlot<*>> get() = setOf(Slots.PLUGIN_XML)

  override suspend fun execute(ctx: ComputeContext) {
    coroutineScope {
      val model = ctx.model
      val graph = model.pluginGraph
      val pluginContentCache = model.pluginContentCache
      val strategy = model.fileUpdater
      val suppressionConfig = model.suppressionConfig
      // Suppression semantics: return TRUE to include dep, FALSE to exclude
      // Entries in filter are deps to SUPPRESS, so return TRUE if dep is NOT in filter
      val dependencyFilter: (String, String, Boolean) -> Boolean = { moduleName, depName, _ ->
        !suppressionConfig.getPluginSuppressedModules(ContentModuleName(moduleName)).contains(ContentModuleName(depName))
      }

      // Process all real plugins in the graph (main target present).
      // DSL-defined plugins are generated from Kotlin specs and skipped here.
      val updateSuppressions = model.updateSuppressions
      val tasks = ArrayList<Deferred<PluginXmlGenResult?>>()
      val pluginGraphDeps = collectPluginGraphDeps(graph, model.config.libraryModuleFilter)
      for (graphDeps in pluginGraphDeps) {
        if (graphDeps.isDslDefined) continue
        tasks.add(async {
          generatePluginXmlDependencies(
            graphDeps = graphDeps,
            pluginContentCache = pluginContentCache,
            dependencyFilter = dependencyFilter,
            suppressionConfig = suppressionConfig,
            strategy = strategy,
            updateSuppressions = updateSuppressions,
            emitError = ctx::emitError,
          )
        })
      }
      val results = tasks.awaitAll().filterNotNull()

      ctx.publish(Slots.PLUGIN_XML, PluginXmlOutput(
        files = results.map { it.toPluginDependencyFileResult() },
        detailedResults = results.map { it.toPluginXmlFileResult() },
      ))
    }
  }
}

internal data class PluginGraphDeps(
  val pluginContentModuleName: ContentModuleName,
  @JvmField val isDslDefined: Boolean,
  @JvmField val isTest: Boolean,
  @JvmField val jpsModuleDependencies: Set<ContentModuleName>,
  @JvmField val jpsPluginDependencies: Set<PluginId>,
  @JvmField val filteredModuleDependencies: Set<ContentModuleName>,
)

internal fun collectPluginGraphDeps(graph: PluginGraph, libraryModuleFilter: (String) -> Boolean): List<PluginGraphDeps> {
  val results = ArrayList<PluginGraphDeps>()
  graph.query {
    plugins { plugin ->
      val pluginName = plugin.contentModuleName()
      val contentModules = HashSet<ContentModuleName>()
      plugin.containsContent { module, _ -> contentModules.add(module.contentName()) }
      plugin.containsContentTest { module, _ -> contentModules.add(module.contentName()) }

      var hasMainTarget = false
      val moduleDeps = LinkedHashSet<ContentModuleName>()
      val pluginDeps = LinkedHashSet<PluginId>()
      val filteredModuleDeps = LinkedHashSet<ContentModuleName>()

      plugin.mainTarget { target ->
        hasMainTarget = true
        target.dependsOn { dep ->
          if (!dep.isProduction()) return@dependsOn
          when (val classification = classifyTarget(dep.targetId)) {
            is DependencyClassification.ModuleDep -> {
              if (classification.moduleName.value.startsWith(LIB_MODULE_PREFIX) && !libraryModuleFilter(classification.moduleName.value)) {
                filteredModuleDeps.add(classification.moduleName)
                return@dependsOn
              }
              if (classification.moduleName in contentModules) {
                filteredModuleDeps.add(classification.moduleName)
                return@dependsOn
              }
              val depModuleId = contentModule(classification.moduleName)?.id ?: -1
              if (depModuleId >= 0 && shouldSkipEmbeddedPluginDependency(depModuleId)) {
                filteredModuleDeps.add(classification.moduleName)
                return@dependsOn
              }
              moduleDeps.add(classification.moduleName)
            }
            is DependencyClassification.PluginDep -> pluginDeps.add(classification.pluginId)
            DependencyClassification.Skip -> {}
          }
        }
      }

      if (!hasMainTarget) return@plugins

      results.add(PluginGraphDeps(
        pluginContentModuleName = pluginName,
        isDslDefined = plugin.isDslDefined,
        isTest = plugin.isTest,
        jpsModuleDependencies = moduleDeps,
        jpsPluginDependencies = pluginDeps,
        filteredModuleDependencies = filteredModuleDeps,
      ))
    }
  }
  return results
}

/**
 * Container for filtered plugin dependencies.
 *
 * @param pluginDependencies Plugin IDs to add as dependencies
 * @param moduleDependencies Module names to add as dependencies (filtered - what gets written to XML)
 * @param preservedPluginIds Plugin IDs that were filtered out but should be preserved if already present.
 *        This ensures filtered deps are neither added nor removed - they remain "frozen" in their current state.
 * @param suppressionUsages Suppression usages recorded during filtering (for unified stale detection)
 */
internal data class FilteredDependencies(
  @JvmField val pluginDependencies: List<PluginId>,
  @JvmField val moduleDependencies: List<ContentModuleName>,
  @JvmField val preservedPluginIds: Set<PluginId> = emptySet(),
  @JvmField val suppressionUsages: List<SuppressionUsage> = emptyList(),
)

/**
 * Internal result type with more detail for inter-generator communication.
 */
private data class PluginXmlGenResult(
  val pluginContentModuleName: ContentModuleName,
  @JvmField val pluginXmlPath: java.nio.file.Path,
  @JvmField val status: org.jetbrains.intellij.build.productLayout.stats.FileChangeStatus,
  @JvmField val dependencyCount: Int,
  /** Suppression usages recorded during generation (for unified stale detection) */
  @JvmField val suppressionUsages: List<SuppressionUsage> = emptyList(),
) {
  fun toPluginDependencyFileResult(): PluginDependencyFileResult {
    return PluginDependencyFileResult(
      pluginContentModuleName = pluginContentModuleName,
      pluginXmlPath = pluginXmlPath,
      status = status,
      dependencyCount = dependencyCount,
      contentModuleResults = emptyList(), // Content modules handled by separate generator
    )
  }

  fun toPluginXmlFileResult(): PluginXmlFileResult {
    return PluginXmlFileResult(
      pluginContentModuleName = pluginContentModuleName,
      pluginXmlPath = pluginXmlPath,
      status = status,
      dependencyCount = dependencyCount,
      suppressionUsages = suppressionUsages,
    )
  }
}

/**
 * Generates dependencies for a single plugin.xml file.
 *
 * Dependencies are derived from the plugin graph (populated from JPS in ModelBuildingStage Phase 8):
 * - Target is a content module → `<module name="..."/>` dependency
 * - Target is a plugin → `<plugin id="..."/>` dependency
 *
 * Also migrates legacy `<depends>` entries (v1 format) to `<plugin id="..."/>` (v2 format).
 */
private suspend fun generatePluginXmlDependencies(
  graphDeps: PluginGraphDeps,
  pluginContentCache: PluginContentProvider,
  dependencyFilter: (moduleName: String, depName: String, isTest: Boolean) -> Boolean,
  suppressionConfig: SuppressionConfig,
  strategy: FileUpdateStrategy,
  updateSuppressions: Boolean,
  emitError: (org.jetbrains.intellij.build.productLayout.model.error.ValidationError) -> Unit,
): PluginXmlGenResult? {
  val pluginContentModuleName = graphDeps.pluginContentModuleName
  val pluginTargetName = TargetName(pluginContentModuleName.value)
  val info = pluginContentCache.getOrExtract(pluginTargetName) ?: return null

  if (info.pluginId == null && info.source != PluginSource.DISCOVERED) {
    emitError(MissingPluginIdError(
      context = pluginContentModuleName.value,
      pluginName = pluginTargetName,
      pluginXmlPath = info.pluginXmlPath,
      pluginSource = info.source.name,
    ))
  }

  val effectiveFilter: (moduleName: String, depName: String, isTest: Boolean) -> Boolean =
    if (graphDeps.isDslDefined) { _, _, _ -> true } else dependencyFilter

  val deps = filterPluginDependencies(graphDeps, info, effectiveFilter, suppressionConfig, updateSuppressions)

  // Legacy <depends> entries are NOT migrated - they stay as-is
  // Generator only manages <dependencies> section, doesn't touch legacy format
  val legacyMigration = LegacyMigrationResult(content = info.pluginXmlContent, pluginDepsToAdd = emptyList())

  // Merge legacy plugin deps with auto-generated plugin deps (convert to String for XML processing)
  val allPluginDepsStrings = (deps.pluginDependencies.map { it.value } + legacyMigration.pluginDepsToAdd).distinct().sorted()

  // Compute xi:include deps from depsByFile (first entry = main file, rest = xi:includes)
  // These are deps already present in xi:included files, so we don't need to add them to the main file
  val xiIncludeModuleDeps = info.depsByFile.drop(1).flatMapTo(HashSet()) { it.moduleDependencies }
  val xiIncludePluginDeps = info.depsByFile.drop(1).flatMapTo(HashSet()) { it.pluginDependencies }

  // Compute JPS deps for preservation check
  val jpsModuleDepNames = deps.moduleDependencies.mapTo(HashSet()) { it.value }
  val jpsPluginDepNames = allPluginDepsStrings.toSet()

  val status = updateXmlDependencies(
    path = info.pluginXmlPath,
    content = legacyMigration.content, // Use content with <depends> removed
    moduleDependencies = deps.moduleDependencies.map { it.value },
    pluginDependencies = allPluginDepsStrings,
    preserveExistingModule = { moduleName ->
      if (updateSuppressions) {
        // Freeze mode: preserve all existing deps not in computed JPS deps
        moduleName !in jpsModuleDepNames
      }
      else {
        // Normal mode: preserve if filter excludes it
        !effectiveFilter(pluginContentModuleName.value, moduleName, false)
      }
    },
    // Preserve existing plugin deps that aren't being auto-added (either filtered out or manually added).
    // This ensures we only ADD deps when detected by JPS and not filtered, never REMOVE existing deps.
    // Note: legacy deps are already included in allPluginDeps, so they won't be preserved separately.
    preserveExistingPlugin = { pluginId ->
      if (updateSuppressions) {
        // Freeze mode: preserve all existing plugin deps not in computed JPS deps
        pluginId !in jpsPluginDepNames
      }
      else {
        // Normal mode: preserve if not being auto-added
        pluginId !in allPluginDepsStrings
      }
    },
    // Pass legacy <depends> plugin IDs for semantic comparison - if computed deps match legacy,
    // don't insert <dependencies> section just to change format
    legacyPluginDependencies = info.legacyDepends.map { it.pluginId.value },
    // Pass xi:include deps so we don't add them to main file when they already exist in xi:includes
    xiIncludeModuleDeps = xiIncludeModuleDeps,
    xiIncludePluginDeps = xiIncludePluginDeps,
    strategy = strategy,
  )

  return PluginXmlGenResult(
    pluginContentModuleName = pluginContentModuleName,
    pluginXmlPath = info.pluginXmlPath,
    status = status,
    dependencyCount = deps.moduleDependencies.size + allPluginDepsStrings.size,
    suppressionUsages = deps.suppressionUsages,
  )
}

/**
 * Filters graph-derived JPS dependencies for a plugin to determine what goes into plugin.xml.
 *
 * Dependencies are computed from the graph (plugin main target → dependsOn) in [collectPluginGraphDeps].
 * This function applies suppression config and "freeze" behavior while preserving existing XML deps.
 *
 * @param graphDeps Graph-derived dependencies for the plugin
 * @param pluginInfo Plugin content info (existing XML deps + file content)
 * @param dependencyFilter Filter to suppress auto-generated module dependencies
 * @param suppressionConfig Configuration for suppressing plugin/module dependencies
 */
internal fun filterPluginDependencies(
  graphDeps: PluginGraphDeps,
  pluginInfo: PluginContentInfo,
  dependencyFilter: (moduleName: String, depName: String, isTest: Boolean) -> Boolean,
  suppressionConfig: SuppressionConfig? = null,
  updateSuppressions: Boolean = false,
): FilteredDependencies {
  val pluginContentModuleName = graphDeps.pluginContentModuleName
  val moduleDeps = mutableListOf<ContentModuleName>()
  val pluginDeps = mutableListOf<PluginId>()
  val preservedPluginIds = LinkedHashSet<PluginId>()
  val suppressionUsages = mutableListOf<SuppressionUsage>()

  // Pre-compute existing XML deps for suppression tracking
  val existingXmlModuleDeps = pluginInfo.moduleDependencies
  val existingXmlPluginDeps: Set<PluginId> = pluginInfo.depsByFile.firstOrNull()?.pluginDependencies ?: emptySet()

  // Get exclusion set for plugin dependencies (plugin IDs to suppress)
  val pluginExclusions = suppressionConfig?.getPluginSuppressedPlugins(pluginContentModuleName) ?: emptySet()

  for (dep in graphDeps.jpsPluginDependencies) {
    val isNewPluginDep = dep !in existingXmlPluginDeps
    if (updateSuppressions && isNewPluginDep) {
      // Freeze mode: suppress new plugin deps to prevent adding them
      preservedPluginIds.add(dep)
      suppressionUsages.add(SuppressionUsage(pluginContentModuleName, dep.value, SuppressionType.PLUGIN_XML_PLUGIN))
    }
    else if (dep in pluginExclusions) {
      // Normal mode: check filter - if plugin ID is in exclusion set, don't add but preserve if existing
      preservedPluginIds.add(dep)
      suppressionUsages.add(SuppressionUsage(pluginContentModuleName, dep.value, SuppressionType.PLUGIN_XML_PLUGIN))
    }
    else {
      pluginDeps.add(dep)
    }
  }

  for (dep in graphDeps.jpsModuleDependencies) {
    val depName = dep.value
    val isNewModuleDep = dep !in existingXmlModuleDeps

    if (updateSuppressions && isNewModuleDep) {
      // Freeze mode: suppress new deps (not in existing XML) to prevent adding them
      suppressionUsages.add(SuppressionUsage(pluginContentModuleName, depName, SuppressionType.PLUGIN_XML_MODULE))
    }
    else if (!dependencyFilter(pluginContentModuleName.value, depName, false)) {
      // Normal mode: use existing suppressions
      suppressionUsages.add(SuppressionUsage(pluginContentModuleName, depName, SuppressionType.PLUGIN_XML_MODULE))
    }
    else {
      moduleDeps.add(dep)
    }
  }

  // Track suppressions that prevent removal: existing XML deps not in JPS
  for (existingDep in existingXmlModuleDeps) {
    val notInJps = existingDep !in graphDeps.jpsModuleDependencies
    if (notInJps) {
      if (updateSuppressions) {
        if (existingDep in graphDeps.filteredModuleDependencies) {
          debug("filterDeps") {
            "preserve filtered dep via suppression for ${pluginContentModuleName.value} -> ${existingDep.value}"
          }
        }
        // Freeze mode: suppress removal of existing deps not in JPS
        suppressionUsages.add(SuppressionUsage(pluginContentModuleName, existingDep.value, SuppressionType.PLUGIN_XML_MODULE))
      }
      else if (!dependencyFilter(pluginContentModuleName.value, existingDep.value, false)) {
        // Normal mode: suppression keeps this XML dep - report it
        suppressionUsages.add(SuppressionUsage(pluginContentModuleName, existingDep.value, SuppressionType.PLUGIN_XML_MODULE))
      }
    }
  }

  // Track plugin suppressions that prevent removal: existing XML plugin deps not in JPS
  val allJpsPluginDeps = graphDeps.jpsPluginDependencies + preservedPluginIds
  for (existingPluginDep in existingXmlPluginDeps) {
    val notInJps = existingPluginDep !in allJpsPluginDeps
    if (notInJps) {
      if (updateSuppressions) {
        // Freeze mode: suppress removal of existing plugin deps not in JPS
        suppressionUsages.add(SuppressionUsage(pluginContentModuleName, existingPluginDep.value, SuppressionType.PLUGIN_XML_PLUGIN))
      }
      else if (existingPluginDep in pluginExclusions) {
        // Normal mode: emit usage if suppression config is actively preserving this dep
        suppressionUsages.add(SuppressionUsage(pluginContentModuleName, existingPluginDep.value, SuppressionType.PLUGIN_XML_PLUGIN))
      }
    }
  }

  return FilteredDependencies(
    pluginDependencies = pluginDeps.distinctBy { it.value }.sortedBy { it.value },
    moduleDependencies = moduleDeps.distinctBy { it.value }.sortedBy { it.value },
    preservedPluginIds = preservedPluginIds,
    suppressionUsages = suppressionUsages,
  )
}

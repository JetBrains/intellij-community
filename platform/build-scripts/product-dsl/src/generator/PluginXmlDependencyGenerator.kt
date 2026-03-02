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
import org.jetbrains.intellij.build.productLayout.dependency.PluginContentProvider
import org.jetbrains.intellij.build.productLayout.deps.PluginDependencyPlan
import org.jetbrains.intellij.build.productLayout.deps.PluginDependencyPlanOutput
import org.jetbrains.intellij.build.productLayout.discovery.PluginContentInfo
import org.jetbrains.intellij.build.productLayout.discovery.PluginSource
import org.jetbrains.intellij.build.productLayout.model.error.MissingPluginIdError
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.DataSlot
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode
import org.jetbrains.intellij.build.productLayout.pipeline.Slots
import org.jetbrains.intellij.build.productLayout.stats.SuppressionType
import org.jetbrains.intellij.build.productLayout.stats.SuppressionUsage
import org.jetbrains.intellij.build.productLayout.xml.LegacyMigrationResult
import org.jetbrains.intellij.build.productLayout.xml.extractDependenciesEntries
import org.jetbrains.intellij.build.productLayout.xml.removeDuplicateLegacyDepends

/**
 * Planner for plugin.xml dependency XML files.
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
 * **Publishes:** [Slots.PLUGIN_DEPENDENCY_PLAN] for downstream writing and validation
 *
 * **No dependencies** - can run immediately (level 0).
 */
internal object PluginDependencyPlanner : PipelineNode {
  override val id get() = NodeIds.PLUGIN_XML_DEPS
  override val produces: Set<DataSlot<*>> get() = setOf(Slots.PLUGIN_DEPENDENCY_PLAN)

  override suspend fun execute(ctx: ComputeContext) {
    coroutineScope {
      val model = ctx.model
      val graph = model.pluginGraph
      val pluginContentCache = model.pluginContentCache
      val suppressionConfig = model.suppressionConfig
      val updateSuppressions = model.updateSuppressions
      val allRealProductNames = embeddedCheckProductNames(model.discovery.products.map { it.name })

      // Process all real plugins in the graph (main target present).
      // DSL-defined plugins are generated from Kotlin specs and skipped here.
      val tasks = ArrayList<Deferred<PluginDependencyPlan?>>()
      val pluginGraphDeps = collectPluginGraphDeps(
        graph = graph,
        allRealProductNames = allRealProductNames,
        libraryModuleFilter = model.config.libraryModuleFilter,
      )
      for (graphDeps in pluginGraphDeps) {
        if (graphDeps.isDslDefined) continue
        tasks.add(async {
          buildPluginDependencyPlan(
            graphDeps = graphDeps,
            pluginContentCache = pluginContentCache,
            suppressionConfig = suppressionConfig,
            updateSuppressions = updateSuppressions,
            emitError = ctx::emitError,
          )
        })
      }
      val plans = tasks.awaitAll().filterNotNull()

      ctx.publish(Slots.PLUGIN_DEPENDENCY_PLAN, PluginDependencyPlanOutput(plans = plans))
    }
  }
}

internal data class PluginGraphDeps(
  val pluginContentModuleName: ContentModuleName,
  @JvmField val isDslDefined: Boolean,
  @JvmField val isTest: Boolean,
  @JvmField val jpsModuleDependencies: Set<ContentModuleName>,
  @JvmField val jpsPluginDependencies: Set<PluginId>,
  /** Plugin deps declared via legacy `<depends ... config-file="...">` in plugin.xml. */
  @JvmField val legacyConfigFilePluginDependencies: Set<PluginId>,
  @JvmField val filteredModuleDependencies: Set<ContentModuleName>,
  @JvmField val duplicateDeclarationPluginIds: Set<PluginId>,
)

internal fun collectPluginGraphDeps(
  graph: PluginGraph,
  allRealProductNames: Set<String>,
  libraryModuleFilter: (String) -> Boolean,
): List<PluginGraphDeps> {
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
      val legacyConfigFilePluginDeps = LinkedHashSet<PluginId>()
      val filteredModuleDeps = LinkedHashSet<ContentModuleName>()
      val duplicateDeclarations = LinkedHashSet<PluginId>()
      val embeddedCheckProductNames = embeddedCheckProductsForPlugin(plugin.id, allRealProductNames)

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
              if (depModuleId >= 0 && shouldSkipEmbeddedPluginDependency(depModuleId, embeddedCheckProductNames)) {
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

      plugin.dependsOnPlugin { dep ->
        val targetId = dep.target().pluginIdOrNull ?: return@dependsOnPlugin
        if (dep.hasLegacyFormat && dep.hasConfigFile) {
          legacyConfigFilePluginDeps.add(targetId)
        }
        if (!dep.hasLegacyFormat || !dep.hasModernFormat) return@dependsOnPlugin
        duplicateDeclarations.add(targetId)
      }

      if (!hasMainTarget) return@plugins

      results.add(PluginGraphDeps(
        pluginContentModuleName = pluginName,
        isDslDefined = plugin.isDslDefined,
        isTest = plugin.isTest,
        jpsModuleDependencies = moduleDeps,
        jpsPluginDependencies = pluginDeps,
        legacyConfigFilePluginDependencies = legacyConfigFilePluginDeps,
        filteredModuleDependencies = filteredModuleDeps,
        duplicateDeclarationPluginIds = duplicateDeclarations,
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
 * @param suppressionUsages Suppression usages recorded during filtering (for unified stale detection)
 */
internal data class FilteredDependencies(
  @JvmField val pluginDependencies: List<PluginId>,
  @JvmField val moduleDependencies: List<ContentModuleName>,
  @JvmField val suppressionUsages: List<SuppressionUsage> = emptyList(),
)

/**
 * Generates dependencies for a single plugin.xml file.
 *
 * Dependencies are derived from the plugin graph (populated from JPS in ModelBuildingStage Phase 8):
 * - Target is a content module → `<module name="..."/>` dependency
 * - Target is a plugin → `<plugin id="..."/>` dependency
 *
 * Also migrates legacy `<depends>` entries (v1 format) to `<plugin id="..."/>` (v2 format).
 */
private suspend fun buildPluginDependencyPlan(
  graphDeps: PluginGraphDeps,
  pluginContentCache: PluginContentProvider,
  suppressionConfig: SuppressionConfig,
  updateSuppressions: Boolean,
  emitError: (org.jetbrains.intellij.build.productLayout.model.error.ValidationError) -> Unit,
): PluginDependencyPlan? {
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

  val existingXmlModuleDeps = info.moduleDependencies
  val existingXmlPluginDeps: Set<PluginId> = info.depsByFile.firstOrNull()?.pluginDependencies ?: emptySet()
  val effectiveJpsPluginDependencies = graphDeps.jpsPluginDependencies - graphDeps.legacyConfigFilePluginDependencies
  val suppressedModules = suppressionConfig.getPluginSuppressedModules(pluginContentModuleName)
  val suppressedPlugins = suppressionConfig.getPluginSuppressedPlugins(pluginContentModuleName)
  val effectiveSuppressedModules = if (updateSuppressions) {
    val missingModulesInXml = graphDeps.jpsModuleDependencies.filterNotTo(LinkedHashSet()) { it in existingXmlModuleDeps }
    val xmlOnlyModules = existingXmlModuleDeps.filterNotTo(LinkedHashSet()) { it in graphDeps.jpsModuleDependencies }
    suppressedModules + missingModulesInXml + xmlOnlyModules
  }
  else {
    suppressedModules
  }
  val effectiveSuppressedPlugins = if (updateSuppressions) {
    val missingPluginsInXml = effectiveJpsPluginDependencies.filterNotTo(LinkedHashSet()) { it in existingXmlPluginDeps }
    val xmlOnlyPlugins = existingXmlPluginDeps.filterNotTo(LinkedHashSet()) { it in effectiveJpsPluginDependencies }
    suppressedPlugins + missingPluginsInXml + xmlOnlyPlugins
  }
  else {
    suppressedPlugins
  }

  val deps = filterPluginDependencies(
    graphDeps = graphDeps,
    pluginInfo = info,
    jpsPluginDependencies = effectiveJpsPluginDependencies,
    suppressedModules = effectiveSuppressedModules,
    suppressedPlugins = effectiveSuppressedPlugins,
  )

  // Remove duplicate legacy <depends> only when modern deps are present or we are generating a <dependencies> section.
  val hasDependenciesSection = extractDependenciesEntries(info.pluginXmlContent) != null
  val hasModernDepsInXIncludes = info.depsByFile.drop(1).any { it.pluginDependencies.isNotEmpty() || it.moduleDependencies.isNotEmpty() }
  val legacyPluginIds = info.legacyDepends.map { it.pluginId.value }.sorted()
  val autoPluginIds = deps.pluginDependencies.map { it.value }.sorted()
  val shouldRemoveLegacyDuplicates = hasDependenciesSection || hasModernDepsInXIncludes || deps.moduleDependencies.isNotEmpty() || autoPluginIds != legacyPluginIds

  val modernPluginIds = HashSet<PluginId>().apply {
    addAll(deps.pluginDependencies)
    for (fileDeps in info.depsByFile) {
      addAll(fileDeps.pluginDependencies)
    }
  }

  val legacyMigration = if (shouldRemoveLegacyDuplicates) {
    removeDuplicateLegacyDepends(info.pluginXmlContent, modernPluginIds)
  }
  else {
    LegacyMigrationResult(content = info.pluginXmlContent)
  }

  // Compute xi:include deps from depsByFile (first entry = main file, rest = xi:includes)
  // These are deps already present in xi:included files, so we don't need to add them to the main file
  val xiIncludeModuleDeps = info.depsByFile.drop(1).flatMapTo(HashSet()) { it.moduleDependencies }
  val xiIncludePluginDeps = info.depsByFile.drop(1).flatMapTo(HashSet()) { it.pluginDependencies }

  // Compute deps to preserve during XML update
  val preserveExistingModuleDeps = existingXmlModuleDeps.filterTo(LinkedHashSet()) { it in effectiveSuppressedModules }
  val preserveExistingPluginDeps = existingXmlPluginDeps.filterTo(LinkedHashSet()) { it in effectiveSuppressedPlugins }
  val effectiveLegacyDepends = info.legacyDepends.filterNot { it.pluginId in legacyMigration.removedLegacyPluginIds }

  return PluginDependencyPlan(
    pluginContentModuleName = pluginContentModuleName,
    pluginXmlPath = info.pluginXmlPath,
    pluginXmlContent = legacyMigration.content,
    moduleDependencies = deps.moduleDependencies.distinctBy { it.value }.sortedBy { it.value },
    pluginDependencies = deps.pluginDependencies.distinctBy { it.value }.sortedBy { it.value },
    legacyPluginDependencies = effectiveLegacyDepends.map { it.pluginId },
    xiIncludeModuleDeps = xiIncludeModuleDeps,
    xiIncludePluginDeps = xiIncludePluginDeps,
    existingXmlModuleDependencies = existingXmlModuleDeps,
    existingXmlPluginDependencies = existingXmlPluginDeps,
    preserveExistingModuleDependencies = preserveExistingModuleDeps,
    preserveExistingPluginDependencies = preserveExistingPluginDeps,
    suppressionUsages = deps.suppressionUsages,
    duplicateDeclarationPluginIds = graphDeps.duplicateDeclarationPluginIds,
  )
}

/**
 * Filters graph-derived JPS dependencies for a plugin to determine what goes into plugin.xml.
 *
 * Dependencies are computed from the graph (plugin main target → dependsOn) in [collectPluginGraphDeps].
 * This function applies the effective suppression sets computed by the caller.
 *
 * @param graphDeps Graph-derived dependencies for the plugin
 * @param pluginInfo Plugin content info (existing XML deps + file content)
 * @param suppressedModules Effective module suppressions (explicit or update-suppressions capture)
 * @param suppressedPlugins Effective plugin suppressions (explicit or update-suppressions capture)
 */
internal fun filterPluginDependencies(
  graphDeps: PluginGraphDeps,
  pluginInfo: PluginContentInfo,
  jpsPluginDependencies: Set<PluginId> = graphDeps.jpsPluginDependencies,
  suppressedModules: Set<ContentModuleName>,
  suppressedPlugins: Set<PluginId>,
): FilteredDependencies {
  val pluginContentModuleName = graphDeps.pluginContentModuleName
  val moduleDeps = mutableListOf<ContentModuleName>()
  val pluginDeps = mutableListOf<PluginId>()
  val suppressionUsages = mutableListOf<SuppressionUsage>()

  // Pre-compute existing XML deps for suppression tracking
  for (dep in jpsPluginDependencies) {
    if (dep in suppressedPlugins) {
      suppressionUsages.add(SuppressionUsage(pluginContentModuleName, dep.value, SuppressionType.PLUGIN_XML_PLUGIN))
    }
    else {
      pluginDeps.add(dep)
    }
  }

  for (dep in graphDeps.jpsModuleDependencies) {
    val depName = dep.value
    if (dep in suppressedModules) {
      suppressionUsages.add(SuppressionUsage(pluginContentModuleName, depName, SuppressionType.PLUGIN_XML_MODULE))
    }
    else {
      moduleDeps.add(dep)
    }
  }

  // Track suppressions that prevent removal: existing XML deps not in JPS
  val existingXmlModuleDeps = pluginInfo.moduleDependencies
  val existingXmlPluginDeps: Set<PluginId> = pluginInfo.depsByFile.firstOrNull()?.pluginDependencies ?: emptySet()
  for (existingDep in existingXmlModuleDeps) {
    val notInJps = existingDep !in graphDeps.jpsModuleDependencies
    if (notInJps) {
      if (existingDep in suppressedModules) {
        suppressionUsages.add(SuppressionUsage(pluginContentModuleName, existingDep.value, SuppressionType.PLUGIN_XML_MODULE))
      }
    }
  }

  // Track plugin suppressions that prevent removal: existing XML plugin deps not in JPS
  val allJpsPluginDeps = jpsPluginDependencies
  for (existingPluginDep in existingXmlPluginDeps) {
    val notInJps = existingPluginDep !in allJpsPluginDeps
    if (notInJps) {
      if (existingPluginDep in suppressedPlugins) {
        suppressionUsages.add(SuppressionUsage(pluginContentModuleName, existingPluginDep.value, SuppressionType.PLUGIN_XML_PLUGIN))
      }
    }
  }

  return FilteredDependencies(
    pluginDependencies = pluginDeps.distinctBy { it.value }.sortedBy { it.value },
    moduleDependencies = moduleDeps.distinctBy { it.value }.sortedBy { it.value },
    suppressionUsages = suppressionUsages,
  )
}

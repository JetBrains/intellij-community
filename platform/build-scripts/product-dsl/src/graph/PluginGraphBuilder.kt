// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "GrazieInspection", "GrazieStyle")

package org.jetbrains.intellij.build.productLayout.graph

import androidx.collection.MutableIntList
import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.EDGE_BACKED_BY
import com.intellij.platform.pluginGraph.EDGE_BUNDLES
import com.intellij.platform.pluginGraph.EDGE_BUNDLES_TEST
import com.intellij.platform.pluginGraph.EDGE_CONTAINS_CONTENT
import com.intellij.platform.pluginGraph.EDGE_CONTAINS_CONTENT_TEST
import com.intellij.platform.pluginGraph.EDGE_CONTAINS_MODULE
import com.intellij.platform.pluginGraph.EDGE_INCLUDES_MODULE_SET
import com.intellij.platform.pluginGraph.EDGE_MAIN_TARGET
import com.intellij.platform.pluginGraph.EDGE_NESTED_SET
import com.intellij.platform.pluginGraph.EDGE_PLUGIN_XML_DEPENDS_ON_CONTENT_MODULE
import com.intellij.platform.pluginGraph.EDGE_PLUGIN_XML_DEPENDS_ON_PLUGIN
import com.intellij.platform.pluginGraph.EDGE_TARGET_DEPENDS_ON
import com.intellij.platform.pluginGraph.LOADING_EMBEDDED
import com.intellij.platform.pluginGraph.LOADING_ON_DEMAND
import com.intellij.platform.pluginGraph.LOADING_OPTIONAL
import com.intellij.platform.pluginGraph.LOADING_REQUIRED
import com.intellij.platform.pluginGraph.MutablePluginGraphStore
import com.intellij.platform.pluginGraph.NODE_CONTENT_MODULE
import com.intellij.platform.pluginGraph.NODE_FLAG_HAS_DESCRIPTOR
import com.intellij.platform.pluginGraph.NODE_FLAG_IS_DSL_DEFINED
import com.intellij.platform.pluginGraph.NODE_FLAG_IS_TEST
import com.intellij.platform.pluginGraph.NODE_FLAG_IS_TEST_DESCRIPTOR
import com.intellij.platform.pluginGraph.NODE_FLAG_SELF_CONTAINED
import com.intellij.platform.pluginGraph.NODE_MODULE_SET
import com.intellij.platform.pluginGraph.NODE_PLUGIN
import com.intellij.platform.pluginGraph.NODE_PRODUCT
import com.intellij.platform.pluginGraph.NODE_TARGET
import com.intellij.platform.pluginGraph.PLUGIN_DEP_LEGACY_MASK
import com.intellij.platform.pluginGraph.PLUGIN_DEP_MODERN_MASK
import com.intellij.platform.pluginGraph.PluginGraph
import com.intellij.platform.pluginGraph.PluginId
import com.intellij.platform.pluginGraph.TEST_DESCRIPTOR_SUFFIX
import com.intellij.platform.pluginGraph.TargetDependencyScope
import com.intellij.platform.pluginGraph.TargetName
import com.intellij.platform.pluginGraph.baseModuleName
import com.intellij.platform.pluginGraph.packEdgeEntry
import com.intellij.platform.pluginGraph.packPluginDepEntry
import com.intellij.platform.pluginGraph.packTargetDependencyEntry
import com.intellij.platform.pluginGraph.storesReverseEdges
import com.intellij.platform.pluginGraph.unpackNodeId
import com.intellij.platform.pluginGraph.unpackPluginDepFormats
import com.intellij.platform.pluginGraph.unpackPluginDepHasConfigFile
import com.intellij.platform.pluginGraph.unpackPluginDepOptional
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.productLayout.LIB_MODULE_PREFIX
import org.jetbrains.intellij.build.productLayout.ModuleSet
import org.jetbrains.intellij.build.productLayout.dependency.ModuleDescriptorCache
import org.jetbrains.intellij.build.productLayout.dependency.PluginContentProvider
import org.jetbrains.intellij.build.productLayout.discovery.PluginContentInfo
import org.jetbrains.intellij.build.productLayout.model.ErrorSink
import org.jetbrains.intellij.build.productLayout.model.error.MissingPluginInGraphError
import org.jetbrains.intellij.build.productLayout.validator.rule.isTestPlugin
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModuleDependency
import org.jetbrains.jps.model.module.JpsModuleReference

/**
 * Builder for [PluginGraph] using AndroidX primitive collections.
 *
 * Used by [org.jetbrains.intellij.build.productLayout.pipeline.ModelBuildingStage] to build the graph incrementally while iterating
 * over products, plugins, and module sets - avoiding duplicate iteration.
 *
 * ## Design Principles
 *
 * 1. **Single iteration**: ModelBuildingStage already iterates over all data,
 *    the builder adds to graph during that iteration.
 * 2. **O(1) upserts**: Uses Object2IntOpenHashMap for name→id lookups.
 * 3. **Columnar storage**: Names and kinds stored in parallel arrays.
 *
 * ## Usage
 *
 * ```kotlin
 * val builder = PluginGraphBuilder()
 *
 * // Add vertices
 * builder.addProduct("IDEA")
 * builder.addPlugin(TargetName("intellij.vcs"), isTest = false, pluginId = PluginId("com.intellij.vcs"))
 *
 * // Add edges
 * builder.linkProductBundlesPlugin("IDEA", TargetName("intellij.vcs"), isTest = false)
 *
 * // Build final graph
 * val graph = builder.build()
 * ```
 *
 * For comprehensive documentation on the PluginGraph model, node types, edge types, and type-safe DSL:
 * - [PluginGraph Documentation](../../docs/plugin-graph.md)
 *
 * @see PluginGraph
 */
internal class PluginGraphBuilder(
  private val store: MutablePluginGraphStore = MutablePluginGraphStore(),
  private val errorSink: ErrorSink? = null,
) {
  // region Vertex Creation (upsert via index)

  /**
   * Add or get a product vertex. Returns the node ID.
   */
  fun addProduct(name: String): Int {
    val existing = store.nameIndex[NODE_PRODUCT].getOrDefault(name, -1)
    if (existing >= 0) return existing

    val id = store.names.size
    store.names.add(name)
    store.kinds.add(NODE_PRODUCT)
    store.mutableNameIndex(NODE_PRODUCT).set(name, id)
    return id
  }

  /**
   * Add or get a plugin vertex with properties set on creation. Returns the node ID.
   */
  fun addPlugin(
    name: TargetName,
    isTest: Boolean,
    isDslDefined: Boolean = false,
    pluginId: PluginId? = null,
    pluginAliases: List<PluginId> = emptyList(),
  ): Int {
    val existing = store.nameIndex[NODE_PLUGIN].getOrDefault(name.value, -1)
    if (existing >= 0) {
      val flags = (if (isTest) NODE_FLAG_IS_TEST else 0) or (if (isDslDefined) NODE_FLAG_IS_DSL_DEFINED else 0)
      if (flags != 0) {
        store.kinds[existing] = store.kinds[existing] or flags
      }
      if (pluginId != null && store.pluginIds.get(existing) == null) {
        store.pluginIds.put(existing, pluginId.value)
      }
      if (pluginAliases.isNotEmpty()) {
        val existingAliases = store.aliases.get(existing)
        if (existingAliases == null) {
          store.aliases.put(existing, pluginAliases.map { it.value }.toTypedArray())
        }
        else {
          val merged = LinkedHashSet<String>()
          for (alias in existingAliases) {
            merged.add(alias)
          }
          for (alias in pluginAliases) {
            merged.add(alias.value)
          }
          if (merged.size != existingAliases.size) {
            store.aliases.put(existing, merged.toTypedArray())
          }
        }
      }
      return existing
    }

    val id = store.names.size
    store.names.add(name.value)
    store.kinds.add(NODE_PLUGIN
              or (if (isTest) NODE_FLAG_IS_TEST else 0)
              or (if (isDslDefined) NODE_FLAG_IS_DSL_DEFINED else 0))
    store.mutableNameIndex(NODE_PLUGIN).set(name.value, id)

    if (pluginId != null) {
      store.pluginIds.put(id, pluginId.value)
    }
    if (pluginAliases.isNotEmpty()) {
      store.aliases.put(id, pluginAliases.map { it.value }.toTypedArray())
    }
    return id
  }

  /**
   * Get existing plugin node ID or throw if not found.
   * Use this when the plugin MUST have been extracted in Phase 1.
   */
  fun requirePlugin(name: TargetName): Int {
    val existing = store.nameIndex[NODE_PLUGIN].getOrDefault(name.value, -1)
    require(existing >= 0) { "Plugin $name not found - must be extracted first (check if module has META-INF/plugin.xml)" }
    return existing
  }

  /**
   * Add or get a module vertex. Returns the node ID.
   */
  fun addModule(name: ContentModuleName): Int {
    val existing = store.nameIndex[NODE_CONTENT_MODULE].getOrDefault(name.value, -1)
    if (existing >= 0) return existing

    val id = store.names.size
    store.names.add(name.value)
    val flags = if (name.value.endsWith("._test")) NODE_FLAG_IS_TEST_DESCRIPTOR else 0
    store.kinds.add(NODE_CONTENT_MODULE or flags)
    store.mutableNameIndex(NODE_CONTENT_MODULE).set(name.value, id)
    store.descriptorFlagsComplete = false
    return id
  }

  /**
   * Mark a content module as having a descriptor on disk, creating the module node if needed.
   */
  fun markContentModuleHasDescriptor(name: ContentModuleName): Int {
    val id = addModule(name)
    store.kinds[id] = store.kinds[id] or NODE_FLAG_HAS_DESCRIPTOR
    return id
  }

  /**
   * Add or get a module set vertex. Returns the node ID.
   */
  fun addModuleSet(name: String, selfContained: Boolean = false): Int {
    val existing = store.nameIndex[NODE_MODULE_SET].getOrDefault(name, -1)
    if (existing >= 0) return existing

    val id = store.names.size
    store.names.add(name)
    store.kinds.add(NODE_MODULE_SET or (if (selfContained) NODE_FLAG_SELF_CONTAINED else 0))
    store.mutableNameIndex(NODE_MODULE_SET).set(name, id)
    return id
  }

  /**
   * Add or get a target vertex (JPS module backing a content module). Returns the node ID.
   */
  fun addTarget(name: TargetName): Int = addTarget(name.value)

  /**
   * Add or get a target vertex by string name. Returns the node ID.
   */
  fun addTarget(name: String): Int {
    val existing = store.nameIndex[NODE_TARGET].getOrDefault(name, -1)
    if (existing >= 0) return existing

    val id = store.names.size
    store.names.add(name)
    store.kinds.add(NODE_TARGET)
    store.mutableNameIndex(NODE_TARGET).set(name, id)
    store.descriptorFlagsComplete = false
    return id
  }

  // endregion

  // region Edge Creation

  /**
   * Link product bundles plugin (production or test).
   * Plugin must be extracted (have EDGE_MAIN_TARGET) - does NOT create plugin node.
   *
   * If the plugin is missing or not extracted and [errorSink] is provided, emits [MissingPluginInGraphError]
   * and returns without creating the edge. If [errorSink] is null, throws [IllegalArgumentException].
   */
  fun linkProductBundlesPlugin(productName: String, pluginName: TargetName, isTest: Boolean) {
    val productId = addProduct(productName)
    val pluginNodeId = store.nameIndex[NODE_PLUGIN].getOrDefault(pluginName.value, -1)
    if (pluginNodeId < 0) {
      if (errorSink != null) {
        errorSink.emit(MissingPluginInGraphError(
          context = "Product $productName bundles plugin ${pluginName.value}",
          productName = productName,
          pluginName = pluginName,
          isTestPlugin = isTest,
        ))
        return
      }
      else {
        error("Plugin $pluginName not found - must be extracted first (check if module has META-INF/plugin.xml)")
      }
    }
    val mainTargets = store.successors(EDGE_MAIN_TARGET, pluginNodeId)
    if (mainTargets == null || mainTargets.size == 0) {
      if (errorSink != null) {
        errorSink.emit(MissingPluginInGraphError(
          context = "Product $productName bundles plugin ${pluginName.value}",
          productName = productName,
          pluginName = pluginName,
          isTestPlugin = isTest,
        ))
        return
      }
      else {
        error("Plugin $pluginName not extracted - must be extracted first (check if module has META-INF/plugin.xml)")
      }
    }
    val edgeType = if (isTest) EDGE_BUNDLES_TEST else EDGE_BUNDLES
    addEdge(productId, pluginNodeId, edgeType)
  }

  /**
   * Link product includes module set.
   */
  fun linkProductIncludesModuleSet(productName: String, moduleSetName: String) {
    val productId = addProduct(productName)
    val moduleSetId = addModuleSet(moduleSetName)
    addEdge(productId, moduleSetId, EDGE_INCLUDES_MODULE_SET)
  }

  /**
   * Link product contains content module directly.
   * Loading mode is packed into edge entries (not stored in edgeLoadingModes map).
   */
  fun linkProductContainsContent(productName: String, contentModuleName: ContentModuleName, loadingMode: ModuleLoadingRuleValue) {
    val productId = addProduct(productName)
    val moduleId = addModule(contentModuleName)
    addContentEdge(productId, moduleId, EDGE_CONTAINS_CONTENT, loadingMode)

    // Also create target vertex and backedBy edge
    val targetName = TargetName(contentModuleName.baseModuleName().value)
    val targetId = addTarget(targetName)
    addEdge(moduleId, targetId, EDGE_BACKED_BY)
  }

  /**
   * Link plugin contains content module.
   * Loading mode is packed into edge entries (not stored in edgeLoadingModes map).
   *
   * @param isTest Whether this is a test plugin. Determines edge type:
   *   - false: [EDGE_CONTAINS_CONTENT] (production)
   *   - true: [EDGE_CONTAINS_CONTENT_TEST] (test)
   */
  fun linkPluginContent(pluginName: TargetName, contentModuleName: ContentModuleName, loadingMode: ModuleLoadingRuleValue, isTest: Boolean) {
    val pluginId = addPlugin(pluginName, isTest = isTest)
    val moduleId = addModule(contentModuleName)
    val edgeType = if (isTest) EDGE_CONTAINS_CONTENT_TEST else EDGE_CONTAINS_CONTENT
    addContentEdge(pluginId, moduleId, edgeType, loadingMode)

    // Also create target vertex and backedBy edge
    val targetName = TargetName(contentModuleName.baseModuleName().value)
    val targetId = addTarget(targetName)
    addEdge(moduleId, targetId, EDGE_BACKED_BY)
  }

  /**
   * Link plugin to its main target (JPS module).
   * Plugin must already exist from extraction.
   */
  fun linkPluginMainTarget(pluginName: TargetName) {
    val pluginId = requirePlugin(pluginName)
    val targetId = addTarget(pluginName)
    addEdge(pluginId, targetId, EDGE_MAIN_TARGET)
  }

  /**
   * Add plugin with all its content in one operation.
   * Encapsulates: `isTestPlugin` detection + addPlugin + linkPluginMainTarget + linkPluginContent for all modules.
   *
   * Used during extraction to build graph directly without intermediate maps.
   */
  fun addPluginWithContent(
    pluginModule: TargetName,
    content: PluginContentInfo,
    testFrameworkContentModules: Set<ContentModuleName>,
  ) {
    val contentModuleNames = content.contentModules.mapTo(HashSet()) { it.name }
    val isTest = content.isTestPlugin || isTestPlugin(pluginModule, contentModuleNames, testFrameworkContentModules)

    addPlugin(pluginModule, isTest = isTest, isDslDefined = content.isDslDefined, pluginId = content.pluginId, pluginAliases = content.pluginAliases)
    linkPluginMainTarget(pluginModule)

    for (module in content.contentModules) {
      val loadingMode = module.loadingMode ?: ModuleLoadingRuleValue.OPTIONAL
      linkPluginContent(pluginName = pluginModule, contentModuleName = module.name, loadingMode = loadingMode, isTest = isTest)
    }
  }

  /**
   * Add plugin.xml dependency edges (plugin + content-module deps).
   * Uses plugin IDs only; aliases must be modeled as dedicated alias nodes.
   * Unresolved IDs become placeholder plugin nodes.
   */
  fun addPluginDependencyEdges(pluginInfos: Map<TargetName, PluginContentInfo>) {
    if (pluginInfos.isEmpty()) {
      return
    }

    // Pre-register all plugins so pluginId index resolves before adding dependencies.
    for ((pluginName, content) in pluginInfos) {
      addPlugin(
        name = pluginName,
        isTest = content.isTestPlugin,
        isDslDefined = content.isDslDefined,
        pluginId = content.pluginId,
        pluginAliases = content.pluginAliases,
      )
    }

    val idIndex = buildPluginIdIndex()
    for ((pluginName, content) in pluginInfos) {
      val sourceId = store.nameIndex[NODE_PLUGIN].getOrDefault(pluginName.value, -1)
      if (sourceId < 0) {
        continue
      }

      for (depId in content.pluginDependencies) {
        addPluginDependencyEdge(
          sourceId = sourceId,
          depId = depId,
          isOptional = false,
          formatMask = PLUGIN_DEP_MODERN_MASK,
          hasConfigFile = false,
          index = idIndex,
        )
      }
      for (legacy in content.legacyDepends) {
        val isOptional = legacy.optional || legacy.configFile != null
        addPluginDependencyEdge(
          sourceId = sourceId,
          depId = legacy.pluginId,
          isOptional = isOptional,
          formatMask = PLUGIN_DEP_LEGACY_MASK,
          hasConfigFile = legacy.configFile != null,
          index = idIndex,
        )
      }
      for (moduleDep in content.moduleDependencies) {
        addPluginModuleDependencyEdge(sourceId, moduleDep)
      }
    }
  }

  internal fun addPluginDependencyEdgeForTest(
    sourcePluginId: Int,
    depId: PluginId,
    isOptional: Boolean,
    formatMask: Int,
    hasConfigFile: Boolean = false,
  ) {
    addPluginDependencyEdge(
      sourceId = sourcePluginId,
      depId = depId,
      isOptional = isOptional,
      formatMask = formatMask,
      hasConfigFile = hasConfigFile,
      index = buildPluginIdIndex(),
    )
  }

  private fun buildPluginIdIndex(): MutableMap<String, MutableList<Int>> {
    val index = HashMap<String, MutableList<Int>>()
    store.nameIndex[NODE_PLUGIN].forEachValue { nodeId ->
      val pluginId = store.pluginIds.get(nodeId)
      if (pluginId != null) {
        recordPluginId(index, pluginId, nodeId)
      }
    }
    return index
  }

  private fun recordPluginId(index: MutableMap<String, MutableList<Int>>, id: String, nodeId: Int) {
    val list = index.getOrPut(id) { ArrayList(1) }
    if (!list.contains(nodeId)) {
      list.add(nodeId)
    }
  }

  private fun addPluginDependencyEdge(
    sourceId: Int,
    depId: PluginId,
    isOptional: Boolean,
    formatMask: Int,
    hasConfigFile: Boolean,
    index: MutableMap<String, MutableList<Int>>,
  ) {
    val targets = index[depId.value]
    if (!targets.isNullOrEmpty()) {
      for (targetId in targets) {
        if (targetId != sourceId) {
          addPluginDependencyEdge(sourceId, targetId, isOptional, formatMask, hasConfigFile)
        }
      }
      return
    }

    val placeholderId = addPlugin(
      name = TargetName(depId.value),
      isTest = false,
      pluginId = depId,
    )
    recordPluginId(index, depId.value, placeholderId)
    if (placeholderId != sourceId) {
      addPluginDependencyEdge(sourceId, placeholderId, isOptional, formatMask, hasConfigFile)
    }
  }

  private fun addPluginDependencyEdge(sourceId: Int, targetId: Int, isOptional: Boolean, formatMask: Int, hasConfigFile: Boolean) {
    val targets = store.getOrCreateSuccessors(EDGE_PLUGIN_XML_DEPENDS_ON_PLUGIN, sourceId)
    upsertPluginDepEntry(targets, targetId, isOptional, formatMask, hasConfigFile)

    if (storesReverseEdges(EDGE_PLUGIN_XML_DEPENDS_ON_PLUGIN)) {
      val sources = store.getOrCreatePredecessors(EDGE_PLUGIN_XML_DEPENDS_ON_PLUGIN, targetId)
      upsertPluginDepEntry(sources, sourceId, isOptional, formatMask, hasConfigFile)
    }
  }

  private fun addPluginModuleDependencyEdge(sourceId: Int, moduleName: ContentModuleName) {
    val targetId = addModule(moduleName)
    addEdge(sourceId, targetId, EDGE_PLUGIN_XML_DEPENDS_ON_CONTENT_MODULE)
  }

  private fun upsertPluginDepEntry(entries: MutableIntList, nodeId: Int, isOptional: Boolean, formatMask: Int, hasConfigFile: Boolean) {
    for (i in 0 until entries.size) {
      val entry = entries[i]
      if (unpackNodeId(entry) == nodeId) {
        val existingOptional = unpackPluginDepOptional(entry)
        val existingFormats = unpackPluginDepFormats(entry)
        val existingConfigFile = unpackPluginDepHasConfigFile(entry)
        val mergedOptional = existingOptional && isOptional
        val mergedFormats = existingFormats or formatMask
        val mergedConfigFile = existingConfigFile || hasConfigFile
        if (mergedOptional != existingOptional || mergedFormats != existingFormats || mergedConfigFile != existingConfigFile) {
          entries[i] = packPluginDepEntry(nodeId, mergedOptional, mergedFormats, mergedConfigFile)
        }
        return
      }
    }
    entries.add(packPluginDepEntry(nodeId, isOptional, formatMask, hasConfigFile))
  }

  internal fun addEdge(source: Int, target: Int, edgeType: Int) {
    store.addEdge(edgeType, source, target)
  }

  /**
   * Add a target dependency edge with packed scope in both directions.
   *
   * Scope is packed into bits 24-26 of the adjacency entry. If [scope] is null
   * and the edge already exists, the existing scope is preserved.
   */
  internal fun addTargetDependencyEdge(source: Int, target: Int, scope: TargetDependencyScope?) {
    val targets = store.getOrCreateSuccessors(EDGE_TARGET_DEPENDS_ON, source)
    upsertTargetDepEntry(targets, target, scope)

    if (storesReverseEdges(EDGE_TARGET_DEPENDS_ON)) {
      val sources = store.getOrCreatePredecessors(EDGE_TARGET_DEPENDS_ON, target)
      upsertTargetDepEntry(sources, source, scope)
    }
  }

  private fun upsertTargetDepEntry(entries: MutableIntList, nodeId: Int, scope: TargetDependencyScope?) {
    for (i in 0 until entries.size) {
      val entry = entries[i]
      if (unpackNodeId(entry) == nodeId) {
        if (scope != null) {
          entries[i] = packTargetDependencyEntry(nodeId, scope)
        }
        return
      }
    }
    entries.add(packTargetDependencyEntry(nodeId, scope))
  }

  /**
   * Add a content edge with loading mode packed into both outEdges and inEdges entries.
   *
   * For content edges (EDGE_CONTAINS_CONTENT, EDGE_CONTAINS_MODULE, EDGE_CONTAINS_CONTENT_TEST),
   * loading mode is packed into bits 24-25:
   * - outEdges[source] stores: targetId | (loadingMode << 24)
   * - inEdges[target] stores: sourceId | (loadingMode << 24)
   *
   * This enables O(1) loading mode access from either direction without map lookup.
   */
  internal fun addContentEdge(source: Int, target: Int, edgeType: Int, loadingMode: ModuleLoadingRuleValue) {
    val packedLoadingMode = loadingMode.toPackedValue()

    // Add to out edges with packed target
    val packedTarget = packEdgeEntry(target, packedLoadingMode)
    val targets = store.getOrCreateSuccessors(edgeType, source)
    // Check if target already exists (by raw ID, ignoring loading mode)
    if (!targets.any { unpackNodeId(it) == target }) {
      targets.add(packedTarget)
    }

    // Add to in edges with packed source
    if (storesReverseEdges(edgeType)) {
      val packedSource = packEdgeEntry(source, packedLoadingMode)
      val sources = store.getOrCreatePredecessors(edgeType, target)
      // Check if source already exists (by raw ID, ignoring loading mode)
      if (!sources.any { unpackNodeId(it) == source }) {
        sources.add(packedSource)
      }
    }
  }

  /**
   * Add an edge with a loading mode property (for test DSL).
   * Uses packed edge format for content edges.
   */
  internal fun addEdgeWithLoadingMode(source: Int, target: Int, edgeType: Int, loadingMode: ModuleLoadingRuleValue) {
    addContentEdge(source, target, edgeType, loadingMode)
  }

  /**
   * Set plugin ID for an existing plugin node (for test DSL).
   */
  internal fun setPluginId(nodeId: Int, id: String) {
    store.pluginIds.put(nodeId, id)
  }

  // endregion

  // region Bulk Operations

  /**
   * Add all modules from a module set recursively.
   */
  fun addModuleSetContent(moduleSet: ModuleSet) {
    addModuleSetContentRecursive(moduleSet)
  }

  /**
   * Mark all modules with descriptors as content modules in the graph.
   *
   * This ensures classifyTarget can treat descriptor-backed targets as ModuleDep
   * even when they are not yet declared as content sources.
   */
  suspend fun markDescriptorModules(descriptorCache: ModuleDescriptorCache) {
    store.descriptorFlagsComplete = false
    val targetNames = ArrayList<String>()
    store.nameIndex[NODE_TARGET].forEachKey { targetNames.add(it) }
    for (targetName in targetNames) {
      if (descriptorCache.getOrAnalyze(targetName) != null) {
        markContentModuleHasDescriptor(ContentModuleName(targetName))
      }
      val testDescriptorName = targetName + TEST_DESCRIPTOR_SUFFIX
      if (descriptorCache.getOrAnalyze(testDescriptorName) != null) {
        markContentModuleHasDescriptor(ContentModuleName(testDescriptorName))
      }
    }

    val moduleNames = ArrayList<String>()
    store.nameIndex[NODE_CONTENT_MODULE].forEachKey { moduleNames.add(it) }
    for (moduleName in moduleNames) {
      val moduleId = store.nameIndex[NODE_CONTENT_MODULE].getOrDefault(moduleName, -1)
      if (moduleId >= 0 && (store.kinds[moduleId] and NODE_FLAG_HAS_DESCRIPTOR) != 0) {
        continue
      }
      if (descriptorCache.getOrAnalyze(moduleName) != null) {
        markContentModuleHasDescriptor(ContentModuleName(moduleName))
      }
    }
    store.descriptorFlagsComplete = true
  }

  private fun addModuleSetContentRecursive(moduleSet: ModuleSet) {
    val moduleSetId = addModuleSet(moduleSet.name, selfContained = moduleSet.selfContained)

    for (module in moduleSet.modules) {
      val moduleId = addModule(module.name)
      addContentEdge(moduleSetId, moduleId, EDGE_CONTAINS_MODULE, module.loading)

      // Create target vertex and backedBy edge
      val targetName = TargetName(module.name.baseModuleName().value)
      val targetId = addTarget(targetName)
      addEdge(moduleId, targetId, EDGE_BACKED_BY)
    }

    for (nestedSet in moduleSet.nestedSets) {
      val nestedId = addModuleSet(nestedSet.name, selfContained = nestedSet.selfContained)
      addEdge(moduleSetId, nestedId, EDGE_NESTED_SET)
      addModuleSetContentRecursive(nestedSet)
    }
  }

  /**
   * Add JPS dependency edges for all targets in the graph.
   */
  fun addJpsDependencies(outputProvider: ModuleOutputProvider, projectLibraryToModuleMap: Map<String, String>) {
    val javaExtensionService = JpsJavaExtensionService.getInstance()
    val processedTargets = HashSet<String>()
    val targetsToProcess = ArrayDeque<String>()
    val libraryMap = projectLibraryToModuleMap.ifEmpty { outputProvider.getProjectLibraryToModuleMap() }

    // Get initial targets from existing nodes
    val targetIndex = store.nameIndex[NODE_TARGET]
    targetIndex.forEachKey { targetsToProcess.add(it) }

    while (targetsToProcess.isNotEmpty()) {
      val targetName = targetsToProcess.removeFirst()
      if (!processedTargets.add(targetName)) continue

      val targetId = targetIndex.getOrDefault(targetName, -1)
      if (targetId < 0) continue
      val jpsModule = outputProvider.findModule(targetName) ?: continue

      for (element in jpsModule.dependenciesList.dependencies) {
        when (element) {
          is JpsModuleDependency -> {
            val depName = element.moduleReference.moduleName
            val depTargetId = addTarget(depName)
            if (depName !in processedTargets) {
              targetsToProcess.addLast(depName)
            }

            val jpsScope = javaExtensionService.getDependencyExtension(element)?.scope ?: JpsJavaDependencyScope.COMPILE
            val scope = jpsScope.toTargetDependencyScope()
            addTargetDependencyEdge(targetId, depTargetId, scope)
          }
          is JpsLibraryDependency -> {
            if (libraryMap.isEmpty()) continue
            val libRef = element.libraryReference
            if (libRef.parentReference is JpsModuleReference) {
              continue
            }
            val libraryModuleName = libraryMap.get(libRef.libraryName) ?: continue
            val depTargetId = addTarget(libraryModuleName)
            ensureLibraryModuleNode(ContentModuleName(libraryModuleName))
            if (libraryModuleName !in processedTargets) {
              targetsToProcess.addLast(libraryModuleName)
            }
            val jpsScope = javaExtensionService.getDependencyExtension(element)?.scope ?: JpsJavaDependencyScope.COMPILE
            val scope = jpsScope.toTargetDependencyScope()
            addTargetDependencyEdge(targetId, depTargetId, scope)
          }
          else -> Unit
        }
      }
    }
  }

  private fun ensureLibraryModuleNode(moduleName: ContentModuleName) {
    if (!moduleName.value.startsWith(LIB_MODULE_PREFIX)) {
      return
    }
    val moduleId = addModule(moduleName)
    val targetName = TargetName(moduleName.baseModuleName().value)
    val targetId = addTarget(targetName)
    addEdge(moduleId, targetId, EDGE_BACKED_BY)
  }

  /**
   * Register non-bundled plugins that are referenced as JPS dependencies.
   *
   * After JPS dependencies are added, some targets may be plugin modules (have META-INF/plugin.xml)
   * but aren't registered as NODE_PLUGIN because they're not explicitly bundled in any product.
   * This method detects and registers them so that [com.intellij.platform.pluginGraph.GraphScope.classifyTarget] can correctly identify
   * plugin dependencies.
   *
   * Uses graph-structural checks instead of name-based lookups to avoid fragile string matching:
   * - EDGE_MAIN_TARGET: if target has incoming edge, it's already a plugin's main module
   * - EDGE_BACKED_BY: if target has incoming edge, it backs a content module (not a standalone plugin)
   *
   * Discovered plugins are also attached to the graph with their content modules so the graph
   * stays the single source of truth for loading modes and module ownership.
   *
   * @param pluginContentCache Cache for extracting plugin info from modules
   * @return Map of newly discovered plugin modules to their extracted content
   */
  suspend fun registerReferencedPlugins(pluginContentCache: PluginContentProvider): Map<TargetName, PluginContentInfo> {
    val targetIndex = store.nameIndex[NODE_TARGET]
    val candidates = ArrayList<TargetName>()

    targetIndex.forEachKey { targetName ->
      val targetId = targetIndex.getOrDefault(targetName, -1)
      if (targetId < 0) return@forEachKey

      // STRUCTURAL: Has incoming EDGE_MAIN_TARGET = already a plugin's main module
      if (store.hasInEdge(EDGE_MAIN_TARGET, targetId)) return@forEachKey

      // STRUCTURAL: Backs a content module = not a standalone plugin
      if (store.hasInEdge(EDGE_BACKED_BY, targetId)) return@forEachKey

      candidates.add(TargetName(targetName))
    }

    val discoveredInfos = coroutineScope {
      candidates.map { pluginModule ->
        async {
          val pluginInfo = pluginContentCache.getOrExtract(pluginModule) ?: return@async null
          pluginModule to pluginInfo
        }
      }.awaitAll().filterNotNull()
    }

    val discovered = LinkedHashMap<TargetName, PluginContentInfo>(discoveredInfos.size)
    for ((pluginModule, pluginInfo) in discoveredInfos) {
      addPlugin(
        name = pluginModule,
        isTest = false,
        isDslDefined = pluginInfo.isDslDefined,
        pluginId = pluginInfo.pluginId,
        pluginAliases = pluginInfo.pluginAliases,
      )
      linkPluginMainTarget(pluginModule)

      for (module in pluginInfo.contentModules) {
        linkPluginContent(
          pluginName = pluginModule,
          contentModuleName = module.name,
          loadingMode = module.loadingMode ?: ModuleLoadingRuleValue.OPTIONAL,
          isTest = false,
        )
      }

      discovered.put(pluginModule, pluginInfo)
    }

    return discovered
  }

  // endregion

  /**
   * Build a snapshot [PluginGraph] with CSR edge storage for fast queries.
   *
   * The snapshot does not share mutable collections with the builder, so it is safe to
   * keep using the builder after this call (e.g., for DSL test plugin expansion).
   */
  fun build(): PluginGraph = PluginGraph(store.freezeSnapshot())

  /**
   * Build the final [PluginGraph] with CSR edge storage for fast queries.
   *
   * This assumes the builder will no longer be mutated after the call.
   */
  fun buildFrozen(): PluginGraph = PluginGraph(store.freeze())
}

/**
 * Converts JPS dependency scope to graph's [TargetDependencyScope].
 *
 * The ordinal values match between enums for efficient conversion.
 */
internal fun JpsJavaDependencyScope.toTargetDependencyScope(): TargetDependencyScope {
  return when (this) {
    JpsJavaDependencyScope.COMPILE -> TargetDependencyScope.COMPILE
    JpsJavaDependencyScope.TEST -> TargetDependencyScope.TEST
    JpsJavaDependencyScope.RUNTIME -> TargetDependencyScope.RUNTIME
    JpsJavaDependencyScope.PROVIDED -> TargetDependencyScope.PROVIDED
  }
}

/** Convert ModuleLoadingRuleValue to packed Int value */
private fun ModuleLoadingRuleValue.toPackedValue(): Int {
  return when (this) {
    ModuleLoadingRuleValue.REQUIRED -> LOADING_REQUIRED
    ModuleLoadingRuleValue.EMBEDDED -> LOADING_EMBEDDED
    ModuleLoadingRuleValue.OPTIONAL -> LOADING_OPTIONAL
    ModuleLoadingRuleValue.ON_DEMAND -> LOADING_ON_DEMAND
  }
}

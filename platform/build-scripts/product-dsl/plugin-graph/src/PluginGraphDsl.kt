// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/**
 * Graph traversal DSL for [PluginGraph].
 *
 * This file contains HOW to traverse the graph:
 * - [GraphScope] - all traversal operations (node iteration, edge traversal, property access)
 * - [ContentSource] - unified content source traversal
 * - [DependencyClassification] - dependency classification for XML generation
 *
 * For WHAT the graph contains (types, constants), see `PluginGraphSchema.kt`.
 *
 * **Usage**: Access all DSL operations via `graph.query { }`:
 * ```kotlin
 * graph.query {
 *   products { product ->
 *     product.bundles { plugin ->
 *       plugin.containsContent { module, _ ->
 *         println(module.name())
 *       }
 *     }
 *   }
 * }
 * ```
 *
 * @see PluginGraph.query for the entry point
 * @see [docs/plugin-graph.md](../../docs/plugin-graph.md)
 */
@file:Suppress("unused", "ReplaceGetOrSet", "ReplacePutWithAssignment", "GrazieInspection", "GrazieStyle")

package com.intellij.platform.pluginGraph

import androidx.collection.IntList
import androidx.collection.MutableIntSet
import androidx.collection.mutableIntListOf
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue

// region ContentSource - Unified content source traversal

/**
 * Kind of content source for a module.
 *
 * Used with [ContentSource] for exhaustive `when` matching during content source traversal.
 */
enum class ContentSourceKind {
  /** Module is contained by a plugin (via EDGE_CONTAINS_CONTENT) */
  PLUGIN,
  /** Module is contained directly by a product (via EDGE_CONTAINS_CONTENT) */
  PRODUCT,
  /** Module is contained by a module set (via EDGE_CONTAINS_MODULE) */
  MODULE_SET,
}

/**
 * Zero-allocation wrapper for content source traversal results.
 *
 * Packs node ID (lower 32 bits) and [ContentSourceKind] ordinal (upper 32 bits) into a Long.
 * Use [kind] with `when` for exhaustive type-safe dispatch, then typed accessors for node access.
 *
 * ```kotlin
 * module.contentProductionSources { source ->
 *   when (source.kind) {
 *     ContentSourceKind.PLUGIN -> source.plugin().name()
 *     ContentSourceKind.PRODUCT -> source.product().name()
 *     ContentSourceKind.MODULE_SET -> source.moduleSet().name()
 *   }
 * }
 * ```
 */
@JvmInline
value class ContentSource @PublishedApi internal constructor(
  @PublishedApi internal val packed: Long,
) {
  /** Node ID of the content source */
  val nodeId: Int get() = (packed and 0xFFFFFFFF).toInt()

  /** Kind of content source for exhaustive `when` matching */
  val kind: ContentSourceKind get() = ContentSourceKind.entries[(packed shr 32).toInt()]

  /** Get as PluginNode (call only when kind == PLUGIN) */
  fun plugin(): PluginNode = PluginNode(nodeId)

  /** Get as ProductNode (call only when kind == PRODUCT) */
  fun product(): ProductNode = ProductNode(nodeId)

  /** Get as ModuleSetNode (call only when kind == MODULE_SET) */
  fun moduleSet(): ModuleSetNode = ModuleSetNode(nodeId)

  companion object {
    /** Create ContentSource from node ID and kind */
    @PublishedApi
    internal fun create(nodeId: Int, kind: ContentSourceKind): ContentSource {
      return ContentSource((kind.ordinal.toLong() shl 32) or (nodeId.toLong() and 0xFFFFFFFFL))
    }
  }
}

/**
 * Invoker for ModuleNode contentProductionSources property.
 *
 * Zero-allocation inline class enabling property-style access with inline invoke operator.
 */
@JvmInline
value class ContentSourceInvoker @PublishedApi internal constructor(
  @PublishedApi internal val moduleId: Int,
)

/**
 * Invoker for ModuleNode contentSources property (production + test plugin content).
 *
 * Zero-allocation inline class enabling property-style access with inline invoke operator.
 */
@JvmInline
value class ContentSourceAllInvoker @PublishedApi internal constructor(
  @PublishedApi internal val moduleId: Int,
)

// endregion

// region GraphScope - Unified DSL for graph traversal

/**
 * Scope class providing all DSL operations for [PluginGraph] traversal.
 *
 * Access via `graph.query { }`. All node iteration, edge traversal, and property accessors
 * are available as members within this scope.
 *
 * **Benefits over context receivers:**
 * - Standard Kotlin (no experimental features)
 * - Single import: `import com.intellij.platform.pluginGraph.*`
 * - Full IDE discoverability
 * - GC-free inline performance preserved
 *
 * @see PluginGraph.query
 */
@JvmInline
value class GraphScope @PublishedApi internal constructor(
   private val graph: PluginGraph,
) {
  /** Internal store accessor for inline functions */
  @PublishedApi
  internal val store: PluginGraphStore
    get() = graph.getCurrentStore()
  // region Node Iteration

  /** Iterate all products */
  inline fun products(action: (ProductNode) -> Unit) {
    store.forEachNodeId(NODE_PRODUCT) { action(ProductNode(it)) }
  }

  /** Iterate all plugins */
  inline fun plugins(action: (PluginNode) -> Unit) {
    store.forEachNodeId(NODE_PLUGIN) { action(PluginNode(it)) }
  }

  /** Iterate all content modules */
  inline fun contentModules(action: (ContentModuleNode) -> Unit) {
    store.forEachNodeId(NODE_CONTENT_MODULE) { action(ContentModuleNode(it)) }
  }

  /** Iterate all module sets */
  inline fun moduleSets(action: (ModuleSetNode) -> Unit) {
    store.forEachNodeId(NODE_MODULE_SET) { action(ModuleSetNode(it)) }
  }

  /** Iterate all targets */
  inline fun targets(action: (TargetNode) -> Unit) {
    store.forEachNodeId(NODE_TARGET) { action(TargetNode(it)) }
  }

  // endregion

  // region Node Lookups

  /** Get product node by name */
  fun product(name: String): ProductNode? {
    val id = store.nodeId(name, NODE_PRODUCT)
    return if (id >= 0) ProductNode(id) else null
  }

  /** Get plugin node by name */
  fun plugin(name: String): PluginNode? {
    val id = store.nodeId(name, NODE_PLUGIN)
    return if (id >= 0) PluginNode(id) else null
  }

  /** Get module node by name */
  fun contentModule(name: ContentModuleName): ContentModuleNode? {
    val id = store.nodeId(name.value, NODE_CONTENT_MODULE)
    return if (id >= 0) ContentModuleNode(id) else null
  }

  /** Get module set node by name */
  fun moduleSet(name: String): ModuleSetNode? {
    val id = store.nodeId(name, NODE_MODULE_SET)
    return if (id >= 0) ModuleSetNode(id) else null
  }

  /** Get target node by name */
  fun target(name: String): TargetNode? {
    val id = store.nodeId(name, NODE_TARGET)
    return if (id >= 0) TargetNode(id) else null
  }

  // endregion

  // region Forward Edge Properties (ProductNode)

  /** Product → Plugin (production) */
  val ProductNode.bundles: EdgeInvoker<PluginNode>
    get() = EdgeInvoker.Companion.create(EDGE_BUNDLES, id)

  /** Product → Plugin (test) */
  val ProductNode.bundlesTest: EdgeInvoker<PluginNode>
    get() = EdgeInvoker.Companion.create(EDGE_BUNDLES_TEST, id)

  /** Product → ModuleSet */
  val ProductNode.includesModuleSet: EdgeInvoker<ModuleSetNode>
    get() = EdgeInvoker.Companion.create(EDGE_INCLUDES_MODULE_SET, id)

  /** Product → Module (direct content, with loading mode) */
  val ProductNode.containsContent: ContentEdgeInvoker
    get() = ContentEdgeInvoker.create(EDGE_CONTAINS_CONTENT, id)


  /** Product → Module (allowed missing in validation) */
  val ProductNode.allowsMissing: EdgeInvoker<ContentModuleNode>
    get() = EdgeInvoker.Companion.create(EDGE_ALLOWS_MISSING, id)

  // endregion

  // region Forward Edge Properties (PluginNode)

  /** Plugin → Module (production content, with loading mode) */
  val PluginNode.containsContent: ContentEdgeInvoker
    get() = ContentEdgeInvoker.create(EDGE_CONTAINS_CONTENT, id)

  /** Plugin → Module (test content, with loading mode) */
  val PluginNode.containsContentTest: ContentEdgeInvoker
    get() = ContentEdgeInvoker.create(EDGE_CONTAINS_CONTENT_TEST, id)

  /** Plugin → Target (main module) */
  val PluginNode.mainTarget: EdgeInvoker<TargetNode>
    get() = EdgeInvoker.Companion.create(EDGE_MAIN_TARGET, id)

  /** Plugin → Plugin (plugin.xml <plugin> deps) */
  val PluginNode.dependsOnPlugin: PluginDependencyInvoker
    get() = PluginDependencyInvoker(id)

  /** Plugin → Content Module (plugin.xml <module> deps) */
  val PluginNode.dependsOnContentModule: EdgeInvoker<ContentModuleNode>
    get() = EdgeInvoker.Companion.create(EDGE_PLUGIN_XML_DEPENDS_ON_CONTENT_MODULE, id)

  // endregion

  // region Forward Edge Properties (ModuleSetNode)

  /** ModuleSet → ModuleSet (nested) */
  val ModuleSetNode.nestedSet: EdgeInvoker<ModuleSetNode>
    get() = EdgeInvoker.Companion.create(EDGE_NESTED_SET, id)

  /** ModuleSet → Module (direct modules in this set, with loading mode) */
  val ModuleSetNode.containsModule: ContentEdgeInvoker
    get() = ContentEdgeInvoker.create(EDGE_CONTAINS_MODULE, id)

  // endregion

  // region Forward Edge Properties (ModuleNode)

  /** Module → Target (JPS module backing content module) */
  val ContentModuleNode.backedBy: EdgeInvoker<TargetNode>
    get() = EdgeInvoker.Companion.create(EDGE_BACKED_BY, id)

  /** Module → Module (production deps) */
  val ContentModuleNode.dependsOn: EdgeInvoker<ContentModuleNode>
    get() = EdgeInvoker.Companion.create(EDGE_CONTENT_MODULE_DEPENDS_ON, id)

  /** Module → Module (test deps) */
  val ContentModuleNode.dependsOnTest: EdgeInvoker<ContentModuleNode>
    get() = EdgeInvoker.Companion.create(EDGE_CONTENT_MODULE_DEPENDS_ON_TEST, id)

  // endregion

  // region Forward Edge Properties (TargetNode)

  /** Target → Target (build target deps with on-demand scope access) */
  val TargetNode.dependsOn: TargetDependencyInvoker
    get() = TargetDependencyInvoker(id)

  // endregion

  // region Reverse Edge Properties (ModuleNode)

  /**
   * Unified production content source traversal for modules.
   *
   * Iterates all sources that contain this module:
   * - Plugins (via EDGE_CONTAINS_CONTENT)
   * - Products (via EDGE_CONTAINS_CONTENT, direct content)
   * - Module sets (via EDGE_CONTAINS_MODULE)
   *
   * ```kotlin
   * module.contentProductionSources { source ->
   *   when (source.kind) {
   *     ContentSourceKind.PLUGIN -> println("Plugin: ${source.name()}")
   *     ContentSourceKind.PRODUCT -> println("Product: ${source.name()}")
   *     ContentSourceKind.MODULE_SET -> println("ModuleSet: ${source.name()}")
   *   }
   * }
   * ```
   */
  val ContentModuleNode.contentProductionSources: ContentSourceInvoker
    get() = ContentSourceInvoker(id)

  /**
   * Unified content source traversal for modules, including test plugin content.
   *
   * Includes all production sources plus test plugin content (EDGE_CONTAINS_CONTENT_TEST).
   */
  val ContentModuleNode.contentSources: ContentSourceAllInvoker
    get() = ContentSourceAllInvoker(id)

  /**
   * Iterate plugins owning this content module.
   *
   * @param includeTestSources whether to include test plugin content owners
   */
  inline fun ContentModuleNode.owningPlugins(
    includeTestSources: Boolean = false,
    crossinline action: (PluginNode) -> Unit,
  ) {
    val seen = MutableIntSet()
    store.forEachPredecessor(EDGE_CONTAINS_CONTENT, id) { packedEntry ->
      val sourceId = unpackNodeId(packedEntry)
      if (store.kind(sourceId) == NODE_PLUGIN && seen.add(sourceId)) {
        action(PluginNode(sourceId))
      }
    }
    if (includeTestSources) {
      store.forEachPredecessor(EDGE_CONTAINS_CONTENT_TEST, id) { packedEntry ->
        val sourceId = unpackNodeId(packedEntry)
        if (store.kind(sourceId) == NODE_PLUGIN && seen.add(sourceId)) {
          action(PluginNode(sourceId))
        }
      }
    }
  }

  private inline fun forEachContentSource(
    moduleId: Int,
    includeTestSources: Boolean,
    action: (ContentSource) -> Unit,
  ) {
    // EDGE_CONTAINS_CONTENT has both plugins and products as sources
    store.forEachPredecessor(EDGE_CONTAINS_CONTENT, moduleId) { packedEntry ->
      val nodeId = unpackNodeId(packedEntry)
      val nodeKind = store.kind(nodeId) and NODE_KIND_MASK
      val sourceKind = when (nodeKind) {
        NODE_PLUGIN -> ContentSourceKind.PLUGIN
        NODE_PRODUCT -> ContentSourceKind.PRODUCT
        else -> return@forEachPredecessor // Skip unknown node types
      }
      action(ContentSource.create(nodeId, sourceKind))
    }
    if (includeTestSources) {
      // EDGE_CONTAINS_CONTENT_TEST is only from test plugins
      store.forEachPredecessor(EDGE_CONTAINS_CONTENT_TEST, moduleId) { packedEntry ->
        val nodeId = unpackNodeId(packedEntry)
        val nodeKind = store.kind(nodeId) and NODE_KIND_MASK
        if (nodeKind != NODE_PLUGIN) return@forEachPredecessor
        action(ContentSource.create(nodeId, ContentSourceKind.PLUGIN))
      }
    }
    // EDGE_CONTAINS_MODULE is only from module sets
    store.forEachPredecessor(EDGE_CONTAINS_MODULE, moduleId) { packedEntry ->
      val nodeId = unpackNodeId(packedEntry)
      action(ContentSource.create(nodeId, ContentSourceKind.MODULE_SET))
    }
  }

  /**
   * Invoke operator for [ContentSourceInvoker] - enables `contentProductionSources { }` syntax.
   *
   * Iterates EDGE_CONTAINS_CONTENT (filtering by node kind for Plugin vs Product)
   * and EDGE_CONTAINS_MODULE (for ModuleSet), calling action with [ContentSource] for each.
   *
   * Note: This is production-only content source traversal. Test plugin content
   * (EDGE_CONTAINS_CONTENT_TEST) is intentionally excluded.
   */
  operator fun ContentSourceInvoker.invoke(action: (ContentSource) -> Unit) {
    forEachContentSource(moduleId, includeTestSources = false, action)
  }

  /**
   * Invoke operator for [ContentSourceAllInvoker] - enables `contentSources { }` syntax.
   *
   * Iterates EDGE_CONTAINS_CONTENT, EDGE_CONTAINS_CONTENT_TEST, and EDGE_CONTAINS_MODULE.
   */
  operator fun ContentSourceAllInvoker.invoke(action: (ContentSource) -> Unit) {
    forEachContentSource(moduleId, includeTestSources = true, action)
  }

  /** Get name of content source (works for all source kinds) */
  fun ContentSource.name(): String = store.name(nodeId)

  // endregion

  // region Reverse Edge Properties (TargetNode)

  /** Target ← Module (which modules are backed by this target) */
  val TargetNode.backedByModules: ReverseEdgeInvoker<ContentModuleNode>
    get() = ReverseEdgeInvoker.Companion.create(EDGE_BACKED_BY, id)

  // endregion

  // region Reverse Edge Properties (ModuleSetNode)

  /** ModuleSet ← ModuleSet (parent module sets that nest this set) */
  val ModuleSetNode.parentSets: ReverseEdgeInvoker<ModuleSetNode>
    get() = ReverseEdgeInvoker.Companion.create(EDGE_NESTED_SET, id)

  /** ModuleSet ← ModuleSet (all ancestor sets, including this set) */
  inline fun ModuleSetNode.ancestorSets(action: (ModuleSetNode) -> Unit) {
    val visited = MutableIntSet()
    val stack = mutableIntListOf()
    stack.add(id)
    while (!stack.isEmpty()) {
      val currentId = stack.removeAt(stack.lastIndex)
      if (!visited.add(currentId)) continue
      action(ModuleSetNode(currentId))
      store.forEachPredecessor(EDGE_NESTED_SET, currentId) { parentId ->
        if (!visited.contains(parentId)) {
          stack.add(parentId)
        }
      }
    }
  }

  /** ModuleSet ← Product (products that include this module set) */
  val ModuleSetNode.includedByProduct: ReverseEdgeInvoker<ProductNode>
    get() = ReverseEdgeInvoker.Companion.create(EDGE_INCLUDES_MODULE_SET, id)

  /** Get all module set names in hierarchy (this set + all parent sets recursively) */
  fun ModuleSetNode.hierarchyNames(): Set<String> {
    val result = HashSet<String>()
    ancestorSets { ancestor -> result.add(ancestor.name()) }
    return result
  }

  // endregion

  // region Reverse Edge Properties (PluginNode)

  /** Plugin ← Product (products that bundle this plugin - production only) */
  val PluginNode.bundledByProduct: ReverseEdgeInvoker<ProductNode>
    get() = ReverseEdgeInvoker.Companion.create(EDGE_BUNDLES, id)

  /** Plugin ← Product (products that bundle this plugin as test plugin) */
  val PluginNode.bundledByProductTest: ReverseEdgeInvoker<ProductNode>
    get() = ReverseEdgeInvoker.Companion.create(EDGE_BUNDLES_TEST, id)

  /** Plugin ← Plugin (plugins that depend on this plugin) */
  val PluginNode.requiredByPlugin: ReverseEdgeInvoker<PluginNode>
    get() = ReverseEdgeInvoker.Companion.create(EDGE_PLUGIN_XML_DEPENDS_ON_PLUGIN, id)

  /** Plugin ← Product: Iterate all products bundling this plugin (production + test) */
  inline fun PluginNode.bundledByProducts(action: (ProductNode) -> Unit) {
    bundledByProduct { product -> action(product) }
    bundledByProductTest { product -> action(product) }
  }

  // endregion

  // region Property Accessors

  /** Product name */
  fun ProductNode.name(): String = store.name(id)

  /** Plugin name */
  fun PluginNode.name(): TargetName = TargetName(store.name(id))

  /** Plugin main module name as typed content module name */
  fun PluginNode.contentModuleName(): ContentModuleName = ContentModuleName(store.name(id))

  /** Plugin: is this a test plugin? */
  val PluginNode.isTest: Boolean get() = store.isTestPlugin(id)

  /** Plugin: plugin ID from <id> element */
  val PluginNode.pluginId: PluginId get() = store.pluginId(id)

  /** Plugin: plugin ID from <id> element, or null if missing */
  val PluginNode.pluginIdOrNull: PluginId? get() = store.pluginIdOrNull(id)

  /** Plugin: does this node have a plugin ID? */
  val PluginNode.hasPluginId: Boolean get() = store.hasPluginId(id)

  /** Plugin: is this DSL-defined (auto-computed dependencies)? */
  val PluginNode.isDslDefined: Boolean get() = store.isDslDefined(id)

  /** Whether this module is a test descriptor (._test suffix) */
  val ContentModuleNode.isTestDescriptor: Boolean get() = store.isTestDescriptor(id)

  /** Whether this module has a descriptor on disk */
  val ContentModuleNode.hasDescriptor: Boolean get() = store.hasDescriptor(id)

  /** Module name */
  fun ContentModuleNode.name(): ContentModuleName = ContentModuleName(store.name(id))

  /** Module name as typed content module name */
  fun ContentModuleNode.contentName(): ContentModuleName = ContentModuleName(store.name(id))

  /** ModuleSet name */
  fun ModuleSetNode.name(): String = store.name(id)

  /** ModuleSet: is this self-contained? */
  val ModuleSetNode.selfContained: Boolean get() = store.isSelfContained(id)

  /** Target name */
  fun TargetNode.name(): String = store.name(id)

  // endregion

  // region Edge Invocation Operators

  /**
   * Invoke operator for ContentEdgeInvoker - passes target module + loading mode.
   */
  inline operator fun ContentEdgeInvoker.invoke(action: (ContentModuleNode, ModuleLoadingRuleValue) -> Unit) {
    store.forEachSuccessor(edgeId, sourceId) { packedEntry ->
      val moduleId = unpackNodeId(packedEntry)
      val loadingMode = packedToLoadingRule(unpackLoadingMode(packedEntry))
      action(ContentModuleNode(moduleId), loadingMode)
    }
  }

  /**
   * Invoke operator for EdgeInvoker - enables `bundles { }` syntax.
   * Callback receives the target node type for explicit chaining.
   */
  inline operator fun <T : TypedNode> EdgeInvoker<T>.invoke(action: (T) -> Unit) {
    val needsUnpack = isPackedEdgeType(edgeId)
    store.forEachSuccessor(edgeId, sourceId) { entry ->
      val nodeId = if (needsUnpack) unpackNodeId(entry) else entry
      @Suppress("UNCHECKED_CAST")
      action((edgeTargetWrapper(edgeId) as (Int) -> T)(nodeId))
    }
  }

  /**
   * Invoke operator for ReverseEdgeInvoker - enables reverse traversal syntax.
   * Callback receives the source node type for explicit chaining.
   */
  inline operator fun <S : TypedNode> ReverseEdgeInvoker<S>.invoke(action: (S) -> Unit) {
    val needsUnpack = isPackedEdgeType(edgeId)
    store.forEachPredecessor(edgeId, targetId) { entry ->
      val nodeId = if (needsUnpack) unpackNodeId(entry) else entry
      @Suppress("UNCHECKED_CAST")
      action((edgeSourceWrapper(edgeId) as (Int) -> S)(nodeId))
    }
  }

  /**
   * Invoke operator for PluginDependencyInvoker.
   * Passes [PluginDependency] to callback - use [PluginDependency.isOptional],
   * [PluginDependency.hasLegacyFormat], [PluginDependency.hasModernFormat],
   * and [PluginDependency.hasConfigFile] for flags.
   */
  inline operator fun PluginDependencyInvoker.invoke(action: (PluginDependency) -> Unit) {
    store.forEachSuccessor(EDGE_PLUGIN_XML_DEPENDS_ON_PLUGIN, sourceId) { packedEntry ->
      action(PluginDependency.create(sourceId, packedEntry))
    }
  }

  /**
   * Invoke operator for TargetDependencyInvoker.
   * Passes [TargetDependency] to callback - use [TargetDependency.scope] for on-demand scope access.
   */
  inline operator fun TargetDependencyInvoker.invoke(action: (TargetDependency) -> Unit) {
    store.forEachSuccessor(EDGE_TARGET_DEPENDS_ON, sourceId) { packedEntry ->
      action(TargetDependency.create(sourceId, packedEntry))
    }
  }

  /** Get dependency scope on-demand */
  fun TargetDependency.scope(): TargetDependencyScope? = unpackTargetDependencyScope(packedEntry)

  /** Check if dependency scope is production (COMPILE/RUNTIME or null) */
  fun TargetDependency.isProduction(): Boolean {
    val scope = scope()
    return scope != TargetDependencyScope.TEST && scope != TargetDependencyScope.PROVIDED
  }

  // endregion

  // region Recursive Traversals

  /**
   * Traverse all modules in this module set and nested sets (BFS).
   * GC-free except for BFS bookkeeping (stack + visited set).
   *
   * @param filter predicate to filter modules (default: all)
   * @param action called for each module passing the filter
   */
  inline fun ModuleSetNode.modulesRecursive(
    crossinline filter: (ContentModuleNode) -> Boolean = { true },
    action: (ContentModuleNode) -> Unit,
  ) {
    val visitedSets = MutableIntSet()
    val visitedModules = MutableIntSet()
    val modules = mutableIntListOf()
    val stack = mutableIntListOf()
    stack.add(id)
    while (!stack.isEmpty()) {
      val setId = stack.removeAt(stack.lastIndex)
      if (!visitedSets.add(setId)) continue
      store.forEachSuccessor(EDGE_CONTAINS_MODULE, setId) { entry ->
        val moduleId = unpackNodeId(entry)
        if (visitedModules.add(moduleId)) {
          modules.add(moduleId)
        }
      }
      store.forEachSuccessor(EDGE_NESTED_SET, setId) { nestedId ->
        if (!visitedSets.contains(nestedId)) {
          stack.add(nestedId)
        }
      }
    }
    if (modules.size == 0) return
    val sorted = IntArray(modules.size)
    for (index in 0 until modules.size) {
      sorted[index] = modules[index]
    }
    sorted.sort()
    for (moduleId in sorted) {
      val module = ContentModuleNode(moduleId)
      if (filter(module)) {
        action(module)
      }
    }
  }

  /**
   * Check if this module set (including nested sets) contains the given module.
   */
  fun ModuleSetNode.containsModuleRecursive(module: ContentModuleNode): Boolean {
    val targetId = module.id
    val visited = MutableIntSet()
    val stack = mutableIntListOf()
    stack.add(id)
    while (!stack.isEmpty()) {
      val setId = stack.removeAt(stack.lastIndex)
      if (!visited.add(setId)) continue
      if (containsEdge(EDGE_CONTAINS_MODULE, setId, targetId)) {
        return true
      }
      store.forEachSuccessor(EDGE_NESTED_SET, setId) { nestedId ->
        if (!visited.contains(nestedId)) {
          stack.add(nestedId)
        }
      }
    }
    return false
  }

  /**
   * Check if this module set includes another module set transitively.
   * Includes self when [target] is the same as this module set.
   */
  fun ModuleSetNode.includesModuleSetRecursive(target: ModuleSetNode): Boolean {
    if (id == target.id) return true
    val visited = MutableIntSet()
    val stack = mutableIntListOf()
    stack.add(id)
    var found = false
    while (!stack.isEmpty() && !found) {
      val setId = stack.removeAt(stack.lastIndex)
      if (!visited.add(setId)) continue
      store.forEachSuccessor(EDGE_NESTED_SET, setId) { nestedId ->
        if (nestedId == target.id) {
          found = true
          return@forEachSuccessor
        }
        if (!visited.contains(nestedId)) {
          stack.add(nestedId)
        }
      }
    }
    return found
  }

  /**
   * Check if a product includes a module set transitively (direct + nested).
   */
  fun ProductNode.includesModuleSetRecursive(target: ModuleSetNode): Boolean {
    val targetId = target.id
    val visited = MutableIntSet()
    val stack = mutableIntListOf()
    var found = false
    store.forEachSuccessor(EDGE_INCLUDES_MODULE_SET, id) { setId ->
      if (setId == targetId) {
        found = true
        return@forEachSuccessor
      }
      if (visited.add(setId)) {
        stack.add(setId)
      }
    }
    while (!stack.isEmpty() && !found) {
      val setId = stack.removeAt(stack.lastIndex)
      store.forEachSuccessor(EDGE_NESTED_SET, setId) { nestedId ->
        if (nestedId == targetId) {
          found = true
          return@forEachSuccessor
        }
        if (visited.add(nestedId)) {
          stack.add(nestedId)
        }
      }
    }
    return found
  }

  /**
   * Check if a product makes the module available via production content sources.
   *
   * A module is considered available if it is provided by ANY of these paths:
   * - Direct product content: [EDGE_CONTAINS_CONTENT] (product -> module)
   * - Included module sets (including nested sets):
   *   [EDGE_INCLUDES_MODULE_SET] + [EDGE_NESTED_SET] -> [EDGE_CONTAINS_MODULE]
   * - Bundled plugins that contain the module: [EDGE_BUNDLES] -> [EDGE_CONTAINS_CONTENT]
   *
   * Note: test content ([EDGE_CONTAINS_CONTENT_TEST]) and test bundles ([EDGE_BUNDLES_TEST])
   * are intentionally not considered here.
   */
  fun ProductNode.containsAvailableContentModule(module: ContentModuleNode): Boolean {
    val targetId = module.id
    if (containsEdge(EDGE_CONTAINS_CONTENT, id, targetId)) {
      return true
    }

    val visitedSets = MutableIntSet()
    val stack = mutableIntListOf()
    store.forEachSuccessor(EDGE_INCLUDES_MODULE_SET, id) { setId ->
      if (visitedSets.add(setId)) {
        stack.add(setId)
      }
    }
    while (!stack.isEmpty()) {
      val setId = stack.removeAt(stack.lastIndex)
      if (containsEdge(EDGE_CONTAINS_MODULE, setId, targetId)) {
        return true
      }
      store.forEachSuccessor(EDGE_NESTED_SET, setId) { nestedId ->
        if (visitedSets.add(nestedId)) {
          stack.add(nestedId)
        }
      }
    }

    var found = false
    store.forEachSuccessor(EDGE_BUNDLES, id) { pluginId ->
      if (found) return@forEachSuccessor
      if (containsEdge(EDGE_CONTAINS_CONTENT, pluginId, targetId)) {
        found = true
      }
    }
    return found
  }

  /**
   * Traverse transitive module dependencies (BFS).
   * GC-free except for BFS bookkeeping (queue + visited set).
   *
   * @param action called for each dependency node
   */
  inline fun ContentModuleNode.transitiveDeps(action: (ContentModuleNode) -> Unit) {
    val visited = MutableIntSet()
    val queue = mutableIntListOf()
    queue.add(id)
    visited.add(id)
    var head = 0
    while (head < queue.size) {
      val currentId = queue[head++]
      store.forEachSuccessor(EDGE_CONTENT_MODULE_DEPENDS_ON, currentId) { nextId ->
        if (visited.add(nextId)) {
          action(ContentModuleNode(nextId))
          queue.add(nextId)
        }
      }
    }
  }

  /**
   * Check if this module has critical loading mode from ANY incoming edge.
   *
   * A module is critical if ANY predecessor (plugin, moduleSet, or product) includes it with
   * REQUIRED or EMBEDDED loading mode.
   */
  fun ContentModuleNode.isCritical(): Boolean = hasCriticalPredecessor(EDGE_CONTAINS_MODULE) || hasCriticalPredecessor(EDGE_CONTAINS_CONTENT)

  /** Check if any predecessor of given edge type has critical loading mode */
  private fun ContentModuleNode.hasCriticalPredecessor(edgeType: Int): Boolean {
    var isCritical = false
    store.forEachPredecessor(edgeType, id) { entry ->
      if (isCriticalLoadingMode(entry)) {
        isCritical = true
      }
    }
    return isCritical
  }

  // endregion

  // region Utility Functions

  /** Get allowed missing module names for a product */
  fun ProductNode.allowedMissingModules(): Set<ContentModuleName> {
    val result = HashSet<ContentModuleName>()
    allowsMissing { module -> result.add(module.name()) }
    return result
  }

  /**
   * Check if a module has content source edges (is declared as content somewhere).
   *
   * A module is "resolved" if it has incoming content source edges:
   * - `EDGE_CONTAINS_CONTENT` (from Plugin or Product)
   * - `EDGE_CONTAINS_MODULE` (from ModuleSet)
   * - `EDGE_CONTAINS_CONTENT_TEST` (for test content)
   *
   * An "orphan" module exists only as a dependency target but has no content declaration.
   * See `docs/validation-rules.md#resolved-vs-orphan-modules` for details.
   *
   * @param moduleId The module node ID
   * @return true if module is declared as content somewhere, false if orphan
   */
  fun hasContentSource(moduleId: Int): Boolean {
    return store.hasInEdge(EDGE_CONTAINS_CONTENT, moduleId) ||
           store.hasInEdge(EDGE_CONTAINS_MODULE, moduleId) ||
           store.hasInEdge(EDGE_CONTAINS_CONTENT_TEST, moduleId)
  }

  /**
   * Check if a module has PRODUCTION content source edges.
   *
   * Returns true only if module is declared as content in:
   * - `EDGE_CONTAINS_CONTENT` (from Plugin or Product - production content)
   * - `EDGE_CONTAINS_MODULE` (from ModuleSet)
   *
   * Does NOT include test content sources (`EDGE_CONTAINS_CONTENT_TEST`).
   * Use this to filter out modules that are only available in test contexts.
   *
   * @param moduleId The module node ID
   * @return true if module is declared as production content somewhere
   */
  fun hasProductionContentSource(moduleId: Int): Boolean {
    return store.hasInEdge(EDGE_CONTAINS_CONTENT, moduleId) ||
           store.hasInEdge(EDGE_CONTAINS_MODULE, moduleId)
  }

  /** Get successor node IDs for an edge type */
  @PublishedApi
  internal fun successors(edgeType: Int, nodeId: Int): IntList? = store.successors(edgeType, nodeId)

  /** Get predecessor node IDs for an edge type */
  @PublishedApi
  internal fun predecessors(edgeType: Int, nodeId: Int): IntList? = store.predecessors(edgeType, nodeId)

  /** Get predecessor node IDs for multiple edge types (bitmask) */
  inline fun predecessorsMulti(edgeMask: Int, nodeId: Int, action: (Int) -> Unit) {
    store.predecessorsMulti(edgeMask, nodeId, action)
  }

  /** Get node kind by ID */
  fun kind(nodeId: Int): Int = store.kind(nodeId)

  /** Get node name by ID */
  fun name(nodeId: Int): String = store.name(nodeId)

  /**
   * Get a node ID by name and kind.
   * Returns -1 if not found.
   */
  fun nodeId(name: String, kind: Int): Int = store.nodeId(name, kind)

  /**
   * Get all nodes with the specified kind as a sequence.
   */
  fun nodes(kind: Int): Sequence<Int> = sequence {
    store.forEachNodeId(kind) { yield(it) }
  }

  /** Node name by ID */
  fun Int.nodeName(): String = store.name(this)

  /** Check if plugin is DSL-defined (auto-computed dependencies) */
  fun isDslDefined(nodeId: Int): Boolean = store.isDslDefined(nodeId)

  // endregion

  // region Dependency Classification

  /**
   * Classifies a JPS target dependency for XML generation.
   *
   * Used by generators to determine whether a dependency should become:
   * - `<module name="..."/>` for content modules (have {moduleName}.xml descriptor)
   * - `<plugin id="..."/>` for plugins (have META-INF/plugin.xml)
   * - Skipped for targets that are neither
   *
   * **Priority**: Module descriptor takes precedence over plugin detection because
   * content modules may share their resources directory with the parent plugin's
   * META-INF/plugin.xml (e.g., intellij.yaml is embedded in yaml plugin and has
   * intellij.yaml.xml descriptor).
   *
   * @param targetId The target node ID from EDGE_TARGET_DEPENDS_ON traversal
   * @return Classification result
   */
  fun classifyTarget(targetId: Int): DependencyClassification {
    val targetName = store.name(targetId)

    // Check if target has a content module descriptor ({moduleName}.xml) FIRST
    // Module descriptor takes priority over plugin detection
    val moduleId = store.nodeId(targetName, NODE_CONTENT_MODULE)
    if (moduleId >= 0 && store.hasDescriptor(moduleId)) {
      return DependencyClassification.ModuleDep(ContentModuleName(targetName))
    }

    // Check if target is a plugin (has META-INF/plugin.xml)
    val pluginId = store.nodeId(targetName, NODE_PLUGIN)
    if (pluginId >= 0) {
      val resolvedPluginId = store.pluginIdOrNull(pluginId)
      if (resolvedPluginId != null) {
        return DependencyClassification.PluginDep(resolvedPluginId)
      }
      return DependencyClassification.Skip
    }

    // Neither content module nor plugin - skip
    return DependencyClassification.Skip
  }

  // endregion
}

/**
 * Result of classifying a JPS target dependency.
 *
 * Used by generators to determine XML output format.
 */
sealed class DependencyClassification {
  /** Target is a resolved content module - add as `<module name="..."/>` */
  data class ModuleDep(val moduleName: ContentModuleName) : DependencyClassification()

  /** Target is a plugin added as `<plugin id="..."/>` */
  data class PluginDep(val pluginId: PluginId) : DependencyClassification()

  /** Target is neither plugin nor resolved content module - skip */
  data object Skip : DependencyClassification()
}

// endregion

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/**
 * Plugin graph schema: data model, constants, and type definitions.
 *
 * This file contains everything that defines WHAT the graph contains:
 * - Node type constants (`NODE_PRODUCT`, `NODE_PLUGIN`, etc.)
 * - Edge type constants (`EDGE_BUNDLES`, `EDGE_CONTAINS_CONTENT`, etc.)
 * - Loading mode constants and packing functions
 * - Type-safe node wrappers (`ProductNode`, `PluginNode`, etc.)
 * - Edge traversal invokers (`EdgeInvoker`, `ContentEdgeInvoker`, `ReverseEdgeInvoker`)
 * - The `PluginGraph` entry point class
 *
 * For HOW to traverse the graph, see [GraphScope] in `PluginGraphDsl.kt`.
 *
 * @see GraphScope for traversal DSL operations
 * @see PluginGraphStore for low-level storage
 * @see [docs/plugin-graph.md](../../docs/plugin-graph.md)
 */
@file:Suppress("ReplaceGetOrSet", "GrazieInspection", "GrazieStyle")

package com.intellij.platform.pluginGraph

import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue

// ============================================================================
// region Node Types
// ============================================================================

/** Node type for product nodes (IDEs) */
const val NODE_PRODUCT: Int = 0

/** Node type for plugin nodes */
const val NODE_PLUGIN: Int = 1

/** Node type for module nodes */
const val NODE_CONTENT_MODULE: Int = 2

/** Node type for module set nodes */
const val NODE_MODULE_SET: Int = 3

/** Node type for JPS/Bazel target nodes (build units) */
const val NODE_TARGET: Int = 4

/** Number of node types (for array sizing) */
internal const val NODE_TYPE_COUNT: Int = 5

// endregion

// ============================================================================
// region Node Flags (packed into upper bits of kinds int)
// ============================================================================

/** Mask to extract node kind from kinds int (lower 8 bits) */
const val NODE_KIND_MASK: Int = 0xFF

/** Flag: plugin is a test plugin */
const val NODE_FLAG_IS_TEST: Int = 1 shl 8

/** Flag: module set is self-contained */
const val NODE_FLAG_SELF_CONTAINED: Int = 1 shl 9

/** Flag: plugin is DSL-defined (auto-computed dependencies) */
const val NODE_FLAG_IS_DSL_DEFINED: Int = 1 shl 10

/** Flag: content module is a test descriptor (._test suffix) */
const val NODE_FLAG_IS_TEST_DESCRIPTOR: Int = 1 shl 11

/** Flag: content module has a descriptor on disk ({moduleName}.xml) */
const val NODE_FLAG_HAS_DESCRIPTOR: Int = 1 shl 12

// endregion

// ============================================================================
// region Edge Types
// ============================================================================

/** Edge type: Product bundles Plugin (production plugins only) */
const val EDGE_BUNDLES: Int = 0

/** Edge type: Product bundles test Plugin */
const val EDGE_BUNDLES_TEST: Int = 1

/** Edge type: Plugin/Product contains Content Module */
const val EDGE_CONTAINS_CONTENT: Int = 2

/** Edge type: Product includes ModuleSet */
const val EDGE_INCLUDES_MODULE_SET: Int = 3

/** Edge type: ModuleSet contains Module */
const val EDGE_CONTAINS_MODULE: Int = 4

/** Edge type: ModuleSet contains nested ModuleSet */
const val EDGE_NESTED_SET: Int = 5

/** Edge type: Module is backed by Target */
const val EDGE_BACKED_BY: Int = 6

/** Edge type: Plugin has main module Target */
const val EDGE_MAIN_TARGET: Int = 7

/** Edge type: Target depends on Target (build target dependencies: JPS today, Bazel later) */
const val EDGE_TARGET_DEPENDS_ON: Int = 8

/** Edge type: Content module depends on content module (production runtime deps, withTests=false) */
const val EDGE_CONTENT_MODULE_DEPENDS_ON: Int = 9

/** Edge type: Content module depends on content module (test deps, withTests=true, superset of prod) */
const val EDGE_CONTENT_MODULE_DEPENDS_ON_TEST: Int = 10

/** Edge type: Test Plugin contains Content Module */
const val EDGE_CONTAINS_CONTENT_TEST: Int = 11

/** Edge type: Product allows Module to be missing in validation */
const val EDGE_ALLOWS_MISSING: Int = 12

/** Edge type: Plugin depends on Plugin (from plugin.xml deps) */
const val EDGE_PLUGIN_XML_DEPENDS_ON_PLUGIN: Int = 13

/** Edge type: Plugin depends on Content Module (from plugin.xml module deps) */
const val EDGE_PLUGIN_XML_DEPENDS_ON_CONTENT_MODULE: Int = 14

/** Number of edge types (for array sizing) */
internal const val EDGE_TYPE_COUNT: Int = 15

// endregion

// ============================================================================
// region Dependency Edge Semantics (naming)
// ============================================================================
//
// Edge names encode the layer where the dependency is expressed:
// - EDGE_TARGET_DEPENDS_ON: build target deps (JPS today, Bazel later); scope packed into adjacency entries.
// - EDGE_CONTENT_MODULE_DEPENDS_ON / EDGE_CONTENT_MODULE_DEPENDS_ON_TEST: runtime deps from XML.
// - EDGE_PLUGIN_XML_DEPENDS_ON_PLUGIN: plugin.xml <plugin> deps; optional + legacy/modern format flags packed per edge.
// - EDGE_PLUGIN_XML_DEPENDS_ON_CONTENT_MODULE: plugin.xml <module> deps (content modules).
//
// endregion

// ============================================================================
// region Edge Type Masks (for multi-edge queries)
// ============================================================================

/** Mask for EDGE_BUNDLES */
const val EDGE_BUNDLES_MASK: Int = 1 shl EDGE_BUNDLES

/** Mask for EDGE_BUNDLES_TEST */
const val EDGE_BUNDLES_TEST_MASK: Int = 1 shl EDGE_BUNDLES_TEST

/** Mask for all bundle edges (production + test) */
const val EDGE_BUNDLES_ALL: Int = EDGE_BUNDLES_MASK or EDGE_BUNDLES_TEST_MASK

/** Mask for EDGE_CONTAINS_CONTENT */
const val EDGE_CONTAINS_CONTENT_MASK: Int = 1 shl EDGE_CONTAINS_CONTENT

/** Mask for EDGE_CONTAINS_CONTENT_TEST */
const val EDGE_CONTAINS_CONTENT_TEST_MASK: Int = 1 shl EDGE_CONTAINS_CONTENT_TEST

/** Mask for all containsContent edges (production + test) */
internal const val EDGE_CONTAINS_CONTENT_ALL: Int = EDGE_CONTAINS_CONTENT_MASK or EDGE_CONTAINS_CONTENT_TEST_MASK

// endregion

// ============================================================================
// region Loading Mode Packing (for content edges)
// ============================================================================

/**
 * Loading mode values packed into bits 24-25 of adjacency list entries.
 * Order matters: REQUIRED=0, EMBEDDED=1 enables criticality check via `<= LOADING_CRITICAL_MAX`.
 */
const val LOADING_REQUIRED: Int = 0
const val LOADING_EMBEDDED: Int = 1
const val LOADING_OPTIONAL: Int = 2
const val LOADING_ON_DEMAND: Int = 3

/** Bit position for loading mode in packed edge entries */
private const val LOADING_MODE_SHIFT: Int = 24

/** Mask to extract node ID from packed entry (bits 0-23, supports 16M nodes) */
private const val NODE_ID_MASK: Int = 0xFFFFFF

/** Maximum loading mode value considered critical (REQUIRED=0 or EMBEDDED=1) */
private const val LOADING_CRITICAL_MAX: Int = LOADING_EMBEDDED

/** Pack node ID with loading mode into single Int */
fun packEdgeEntry(nodeId: Int, loadingMode: Int): Int {
  require(nodeId <= NODE_ID_MASK) { "nodeId $nodeId exceeds 24-bit limit ($NODE_ID_MASK)" }
  require(loadingMode <= 3) { "loadingMode $loadingMode exceeds 2-bit limit" }
  return nodeId or (loadingMode shl LOADING_MODE_SHIFT)
}

/** Unpack node ID from packed entry */
fun unpackNodeId(packedEntry: Int): Int = packedEntry and NODE_ID_MASK

/** Unpack loading mode from packed entry */
fun unpackLoadingMode(packedEntry: Int): Int = (packedEntry shr LOADING_MODE_SHIFT) and 0x3

/** Check if packed entry has critical loading mode (REQUIRED or EMBEDDED) */
internal fun isCriticalLoadingMode(packedEntry: Int): Boolean = unpackLoadingMode(packedEntry) <= LOADING_CRITICAL_MAX

/** Convert packed loading mode Int to ModuleLoadingRuleValue */
fun packedToLoadingRule(packed: Int): ModuleLoadingRuleValue {
  return when (packed) {
    LOADING_REQUIRED -> ModuleLoadingRuleValue.REQUIRED
    LOADING_EMBEDDED -> ModuleLoadingRuleValue.EMBEDDED
    LOADING_OPTIONAL -> ModuleLoadingRuleValue.OPTIONAL
    LOADING_ON_DEMAND -> ModuleLoadingRuleValue.ON_DEMAND
    else -> ModuleLoadingRuleValue.OPTIONAL // fallback
  }
}

// endregion

// ============================================================================
// region Plugin Dependency Packing (for EDGE_PLUGIN_XML_DEPENDS_ON_PLUGIN)
// ============================================================================

/** Bit position for optional flag in packed plugin-dep entries */
const val PLUGIN_DEP_OPTIONAL_SHIFT: Int = 24

/** Bit position for legacy format flag in packed plugin-dep entries */
const val PLUGIN_DEP_LEGACY_SHIFT: Int = 25

/** Bit position for modern format flag in packed plugin-dep entries */
const val PLUGIN_DEP_MODERN_SHIFT: Int = 26

/** Bit position for legacy config-file flag in packed plugin-dep entries */
const val PLUGIN_DEP_CONFIG_FILE_SHIFT: Int = 27

/** Mask for optional flag in packed plugin-dep entries */
const val PLUGIN_DEP_OPTIONAL_MASK: Int = 1 shl PLUGIN_DEP_OPTIONAL_SHIFT

/** Mask for legacy format flag in packed plugin-dep entries */
const val PLUGIN_DEP_LEGACY_MASK: Int = 1 shl PLUGIN_DEP_LEGACY_SHIFT

/** Mask for modern format flag in packed plugin-dep entries */
const val PLUGIN_DEP_MODERN_MASK: Int = 1 shl PLUGIN_DEP_MODERN_SHIFT

/** Mask for legacy config-file flag in packed plugin-dep entries */
const val PLUGIN_DEP_CONFIG_FILE_MASK: Int = 1 shl PLUGIN_DEP_CONFIG_FILE_SHIFT

/** Mask for format flags in packed plugin-dep entries */
const val PLUGIN_DEP_FORMAT_MASK: Int = PLUGIN_DEP_LEGACY_MASK or PLUGIN_DEP_MODERN_MASK

/** Pack node ID with optional + format flags into single Int */
fun packPluginDepEntry(nodeId: Int, isOptional: Boolean, formatMask: Int, hasConfigFile: Boolean = false): Int {
  require(nodeId <= NODE_ID_MASK) { "nodeId $nodeId exceeds 24-bit limit ($NODE_ID_MASK)" }
  val optionalBit = if (isOptional) PLUGIN_DEP_OPTIONAL_MASK else 0
  val configFileBit = if (hasConfigFile) PLUGIN_DEP_CONFIG_FILE_MASK else 0
  return nodeId or optionalBit or (formatMask and PLUGIN_DEP_FORMAT_MASK) or configFileBit
}

/** Unpack optional flag from packed plugin-dep entry */
fun unpackPluginDepOptional(packedEntry: Int): Boolean = (packedEntry and PLUGIN_DEP_OPTIONAL_MASK) != 0

/** Unpack format flags from packed plugin-dep entry */
fun unpackPluginDepFormats(packedEntry: Int): Int = packedEntry and PLUGIN_DEP_FORMAT_MASK

/** Check if packed plugin-dep entry includes legacy format */
fun unpackPluginDepHasLegacy(packedEntry: Int): Boolean = (packedEntry and PLUGIN_DEP_LEGACY_MASK) != 0

/** Check if packed plugin-dep entry includes modern format */
fun unpackPluginDepHasModern(packedEntry: Int): Boolean = (packedEntry and PLUGIN_DEP_MODERN_MASK) != 0

/** Check if packed plugin-dep entry includes legacy config-file marker */
fun unpackPluginDepHasConfigFile(packedEntry: Int): Boolean = (packedEntry and PLUGIN_DEP_CONFIG_FILE_MASK) != 0

// endregion

// ============================================================================
// region Target Dependency Packing (for EDGE_TARGET_DEPENDS_ON)
// ============================================================================

/** Bit position for target dependency scope in packed entries. */
const val TARGET_DEP_SCOPE_SHIFT: Int = 24

/** Mask for target dependency scope bits (3 bits: 0 = unknown, 1..4 = scope ordinal + 1). */
private const val TARGET_DEP_SCOPE_MASK: Int = 0x7

/** Pack node ID with optional [TargetDependencyScope] into a single Int. */
fun packTargetDependencyEntry(nodeId: Int, scope: TargetDependencyScope?): Int {
  require(nodeId <= NODE_ID_MASK) { "nodeId $nodeId exceeds 24-bit limit ($NODE_ID_MASK)" }
  val encodedScope = scope?.ordinal?.plus(1) ?: 0
  require(encodedScope in 0..4) { "scope ordinal $encodedScope out of range" }
  return nodeId or (encodedScope shl TARGET_DEP_SCOPE_SHIFT)
}

/** Unpack [TargetDependencyScope] from packed target dependency entry. */
fun unpackTargetDependencyScope(packedEntry: Int): TargetDependencyScope? {
  val encodedScope = (packedEntry ushr TARGET_DEP_SCOPE_SHIFT) and TARGET_DEP_SCOPE_MASK
  if (encodedScope == 0) return null
  return TargetDependencyScope.fromOrdinal(encodedScope - 1)
}

// endregion

// ============================================================================
// region TypedNode - Base interface for type-safe node wrappers
// ============================================================================

/**
 * Base interface for typed node wrappers.
 *
 * All node types ([ProductNode], [PluginNode], etc.) implement this interface,
 * enabling generic traversal while maintaining type safety through sealed hierarchy.
 */
sealed interface TypedNode {
  val id: Int
}

/** Product node - IDEs like IDEA, WebStorm, etc. */
@JvmInline
value class ProductNode(override val id: Int) : TypedNode

/** Plugin node - bundled plugins */
@JvmInline
value class PluginNode(override val id: Int) : TypedNode

/** Module node - content modules */
@JvmInline
value class ContentModuleNode(override val id: Int) : TypedNode

/** Module set node - groups of modules */
@JvmInline
value class ModuleSetNode(override val id: Int) : TypedNode

/** Target node - JPS/Bazel build targets */
@JvmInline
value class TargetNode(override val id: Int) : TypedNode

// endregion

// ============================================================================
// region Edge Invokers - GC-free edge traversal
// ============================================================================

/**
 * Value class for GC-free forward edge traversal (successors).
 *
 * Packs `[edgeId:16][sourceId:32]` into Long for zero-allocation edge traversal.
 *
 * @param T phantom type parameter for compile-time safety (target node type)
 * @see GraphScope for the invoke operator that executes the traversal
 */
@JvmInline
value class EdgeInvoker<out T : TypedNode> private constructor(
  /** Packed value: `[edgeId:16][sourceId:32]` */
  @PublishedApi internal val packed: Long,
) {
  /** Extract edge type ID from upper 16 bits */
  @PublishedApi
  internal val edgeId: Int get() = (packed ushr 32).toInt()

  /** Extract source node ID from lower 32 bits */
  @PublishedApi
  internal val sourceId: Int get() = packed.toInt()

  companion object {
    /** Create EdgeInvoker by packing edgeId and sourceId into Long */
    fun <T : TypedNode> create(edgeId: Int, sourceId: Int): EdgeInvoker<T> {
      require(edgeId >= 0) { "edgeId $edgeId must be non-negative" }
      require(sourceId >= 0) { "sourceId $sourceId must be non-negative" }
      return EdgeInvoker((edgeId.toLong() shl 32) or (sourceId.toLong() and 0xFFFFFFFFL))
    }
  }
}

/**
 * Value class for GC-free traversal of content edges with loading mode.
 *
 * Packs `[edgeId:16][sourceId:32]` into Long, like [EdgeInvoker], but the
 * invoke operator exposes the per-edge loading mode packed in adjacency entries.
 *
 * Used for content edges:
 * - [EDGE_CONTAINS_CONTENT]
 * - [EDGE_CONTAINS_CONTENT_TEST]
 * - [EDGE_CONTAINS_MODULE]
 */
@JvmInline
value class ContentEdgeInvoker @PublishedApi internal constructor(
  /** Packed value: `[edgeId:16][sourceId:32]` */
  @PublishedApi internal val packed: Long,
) {
  /** Extract edge type ID from upper 16 bits */
  @PublishedApi
  internal val edgeId: Int get() = (packed ushr 32).toInt()

  /** Extract source node ID from lower 32 bits */
  @PublishedApi
  internal val sourceId: Int get() = packed.toInt()

  companion object {
    /** Create ContentEdgeInvoker by packing edgeId and sourceId into Long */
    fun create(edgeId: Int, sourceId: Int): ContentEdgeInvoker {
      require(edgeId == EDGE_CONTAINS_CONTENT || edgeId == EDGE_CONTAINS_CONTENT_TEST || edgeId == EDGE_CONTAINS_MODULE) {
        "edgeId $edgeId must be a content edge"
      }
      require(sourceId >= 0) { "sourceId $sourceId must be non-negative" }
      return ContentEdgeInvoker((edgeId.toLong() shl 32) or (sourceId.toLong() and 0xFFFFFFFFL))
    }
  }
}

/**
 * Value class for GC-free reverse edge traversal (predecessors).
 *
 * Packs `[edgeId:16][targetId:32]` into Long for zero-allocation traversal.
 * Mirror of [EdgeInvoker] but traverses incoming edges instead of outgoing.
 *
 * @param S phantom type parameter for compile-time safety (source node type)
 */
@JvmInline
value class ReverseEdgeInvoker<out S : TypedNode> @PublishedApi internal constructor(
  /** Packed value: `[edgeId:16][targetId:32]` */
  @PublishedApi internal val packed: Long,
) {
  /** Extract edge type ID from upper 16 bits */
  @PublishedApi
  internal val edgeId: Int get() = (packed ushr 32).toInt()

  /** Extract target node ID from lower 32 bits */
  @PublishedApi
  internal val targetId: Int get() = packed.toInt()

  companion object {
    /** Create ReverseEdgeInvoker by packing edgeId and targetId into Long */
    fun <S : TypedNode> create(edgeId: Int, targetId: Int): ReverseEdgeInvoker<S> {
      require(edgeId >= 0) { "edgeId $edgeId must be non-negative" }
      require(targetId >= 0) { "targetId $targetId must be non-negative" }
      return ReverseEdgeInvoker((edgeId.toLong() shl 32) or (targetId.toLong() and 0xFFFFFFFFL))
    }
  }
}

/**
 * Zero-allocation wrapper for target dependency traversal with scope access.
 *
 * Packs sourceId (upper 32 bits) + packed target entry (lower 32 bits) into Long.
 * Use [GraphScope.scope] to decode scope on-demand.
 */
@JvmInline
value class TargetDependency @PublishedApi internal constructor(
  @PublishedApi internal val packed: Long,
) {
  /** Packed edge entry (targetId + optional scope) */
  @PublishedApi
  internal val packedEntry: Int get() = packed.toInt()

  /** Target node ID */
  val targetId: Int get() = unpackNodeId(packedEntry)

  /** Get target as TargetNode */
  fun target(): TargetNode = TargetNode(targetId)

  companion object {
    @PublishedApi
    internal fun create(sourceId: Int, packedEntry: Int): TargetDependency {
      return TargetDependency((sourceId.toLong() shl 32) or (packedEntry.toLong() and 0xFFFFFFFFL))
    }
  }
}

/**
 * Invoker for target dependency traversal.
 *
 * Zero-allocation inline class enabling property-style access with inline invoke operator.
 */
@JvmInline
value class TargetDependencyInvoker @PublishedApi internal constructor(
  @PublishedApi internal val sourceId: Int,
)

/**
 * Zero-allocation wrapper for plugin dependency traversal with optional + format flag access.
 *
 * Packs sourceId (upper 32 bits) + packed edge entry (lower 32 bits).
 * Use [isOptional], [hasLegacyFormat], [hasModernFormat], and [hasConfigFile] for flags.
 */
@JvmInline
value class PluginDependency @PublishedApi internal constructor(
  @PublishedApi internal val packed: Long,
) {
  /** Target plugin node ID */
  val targetId: Int get() = unpackNodeId(packed.toInt())

  /** Whether this dependency is optional */
  val isOptional: Boolean get() = unpackPluginDepOptional(packed.toInt())

  /** Whether legacy <depends> format is present for this dependency */
  val hasLegacyFormat: Boolean get() = unpackPluginDepHasLegacy(packed.toInt())

  /** Whether modern <dependencies><plugin> format is present for this dependency */
  val hasModernFormat: Boolean get() = unpackPluginDepHasModern(packed.toInt())

  /** Whether this dependency is declared via legacy `<depends ... config-file="...">` */
  val hasConfigFile: Boolean get() = unpackPluginDepHasConfigFile(packed.toInt())

  /** Get target as PluginNode */
  fun target(): PluginNode = PluginNode(targetId)

  companion object {
    @PublishedApi
    internal fun create(sourceId: Int, packedEntry: Int): PluginDependency {
      return PluginDependency((sourceId.toLong() shl 32) or (packedEntry.toLong() and 0xFFFFFFFFL))
    }
  }
}

/**
 * Invoker for plugin dependency traversal.
 */
@JvmInline
value class PluginDependencyInvoker @PublishedApi internal constructor(
  @PublishedApi internal val sourceId: Int,
)

// endregion

// ============================================================================
// region Edge Type Utilities
// ============================================================================

/** Check if edge type uses packed loading mode format */
internal fun isContentEdgeType(edgeId: Int): Boolean {
  return edgeId == EDGE_CONTAINS_CONTENT || edgeId == EDGE_CONTAINS_MODULE || edgeId == EDGE_CONTAINS_CONTENT_TEST
}

/** Check if edge type uses any packed entry format */
@PublishedApi
internal fun isPackedEdgeType(edgeId: Int): Boolean {
  return isContentEdgeType(edgeId) || edgeId == EDGE_PLUGIN_XML_DEPENDS_ON_PLUGIN || edgeId == EDGE_TARGET_DEPENDS_ON
}

/**
 * Whether reverse adjacency should be stored eagerly for this edge type.
 *
 * Edge types not listed here build reverse CSR lazily from the forward adjacency.
 */
fun storesReverseEdges(edgeId: Int): Boolean {
  return when (edgeId) {
    EDGE_BUNDLES,
    EDGE_BUNDLES_TEST,
    EDGE_CONTAINS_CONTENT,
    EDGE_CONTAINS_CONTENT_TEST,
    EDGE_CONTAINS_MODULE,
    EDGE_INCLUDES_MODULE_SET,
    EDGE_NESTED_SET,
    EDGE_BACKED_BY,
    EDGE_MAIN_TARGET,
    EDGE_PLUGIN_XML_DEPENDS_ON_PLUGIN,
    -> true
    else -> false
  }
}

/** Lookup target wrapper function by edge ID (for forward traversal) */
@PublishedApi
internal fun edgeTargetWrapper(edgeId: Int): (Int) -> TypedNode {
  return when (edgeId) {
    EDGE_BUNDLES, EDGE_BUNDLES_TEST, EDGE_PLUGIN_XML_DEPENDS_ON_PLUGIN -> ::PluginNode
    EDGE_CONTAINS_CONTENT, EDGE_CONTAINS_CONTENT_TEST, EDGE_CONTAINS_MODULE, EDGE_ALLOWS_MISSING, EDGE_PLUGIN_XML_DEPENDS_ON_CONTENT_MODULE -> ::ContentModuleNode
    EDGE_INCLUDES_MODULE_SET, EDGE_NESTED_SET -> ::ModuleSetNode
    EDGE_BACKED_BY, EDGE_MAIN_TARGET, EDGE_TARGET_DEPENDS_ON -> ::TargetNode
    EDGE_CONTENT_MODULE_DEPENDS_ON, EDGE_CONTENT_MODULE_DEPENDS_ON_TEST -> ::ContentModuleNode
    else -> throw IllegalArgumentException("Unknown edge type: $edgeId")
  }
}

/** Lookup source wrapper function by edge ID (for reverse traversal) */
@PublishedApi
internal fun edgeSourceWrapper(edgeId: Int): (Int) -> TypedNode {
  return when (edgeId) {
    EDGE_BUNDLES, EDGE_BUNDLES_TEST -> ::ProductNode
    EDGE_CONTAINS_CONTENT, EDGE_CONTAINS_CONTENT_TEST -> ::PluginNode  // Note: can also be ProductNode
    EDGE_CONTAINS_MODULE -> ::ModuleSetNode
    EDGE_INCLUDES_MODULE_SET -> ::ProductNode
    EDGE_NESTED_SET -> ::ModuleSetNode
    EDGE_BACKED_BY -> ::ContentModuleNode
    EDGE_MAIN_TARGET, EDGE_PLUGIN_XML_DEPENDS_ON_PLUGIN, EDGE_PLUGIN_XML_DEPENDS_ON_CONTENT_MODULE -> ::PluginNode
    EDGE_TARGET_DEPENDS_ON -> ::TargetNode
    EDGE_ALLOWS_MISSING -> ::ProductNode
    EDGE_CONTENT_MODULE_DEPENDS_ON, EDGE_CONTENT_MODULE_DEPENDS_ON_TEST -> ::ContentModuleNode
    else -> throw IllegalArgumentException("Unknown edge type: $edgeId")
  }
}

// endregion

// ============================================================================
// region Edge Map Key Packing
// ============================================================================

/**
 * Pack edge type + node ID into a single Int key for edge map storage.
 *
 * Format: `[edgeType:8][nodeId:24]`
 * - Bits 24-31: edge type (supports 256 types, we use 14)
 * - Bits 0-23: node ID (supports 16M nodes)
 *
 * This enables consolidating 26 separate edge maps (13 out + 13 in) into just 2 maps.
 */
@PublishedApi
internal fun packEdgeMapKey(edgeType: Int, nodeId: Int): Int {
  require(nodeId <= NODE_ID_MASK) { "nodeId $nodeId exceeds 24-bit limit ($NODE_ID_MASK)" }
  require(edgeType < 256) { "edgeType $edgeType exceeds 8-bit limit" }
  return (edgeType shl 24) or nodeId
}

/** Unpack edge type from packed edge map key */
internal fun unpackEdgeType(packedKey: Int): Int = packedKey ushr 24

/** Unpack node ID from packed edge map key */
internal fun unpackEdgeNodeId(packedKey: Int): Int = packedKey and 0xFFFFFF

// endregion

// ============================================================================
// region PluginGraph - Main entry point
// ============================================================================

/**
 * Unified graph model for plugin/module/product relationships.
 *
 * The store reference is volatile for thread-safe atomic swap during incremental updates.
 * Individual stores are effectively immutable after construction - mutations create new stores.
 *
 * **Usage**: Access all DSL operations via `query { }`:
 * ```kotlin
 * graph.query {
 *   products {
 *     bundles {
 *       containsContent { module, _ ->
 *         println(module.name())
 *       }
 *     }
 *   }
 * }
 * ```
 *
 * @see GraphScope for all DSL operations
 * See `org.jetbrains.intellij.build.productLayout.graph.PluginGraphBuilder` for building the graph.
 * @see [docs/plugin-graph.md](../../docs/plugin-graph.md)
 */
class PluginGraph(
  @Volatile private var store: PluginGraphStore,
) {
  // region DSL Entry Point

  /**
   * Execute a block with access to all graph traversal DSL operations.
   *
   * This is the primary entry point for querying the graph. All node iteration,
   * edge traversal, and property accessors are available within the scope.
   *
   * ```kotlin
   * graph.query {
    *   products {
    *     bundles { plugin ->
    *       containsContent { module, _ ->
    *         println("${plugin.name()} contains ${module.name()}")
    *       }
    *     }
    *   }
   * }
   * ```
   *
   * @param block the operations to perform within the graph scope
   * @return the result of the block
   */
  inline fun <R> query(block: GraphScope.() -> R): R = GraphScope(this).block()

  // endregion

  @PublishedApi
  internal fun getCurrentStore(): PluginGraphStore = store

  /**
   * Get the current store for builder/mutation operations.
   * For DSL queries, use [query] instead.
   */
  fun storeForBuilder(): PluginGraphStore = store

  /** True if descriptor presence flags are complete for the current graph snapshot. */
  val descriptorFlagsComplete: Boolean
    get() = store.descriptorFlagsComplete

  fun setCurrentStore(store: PluginGraphStore) {
    this.store = store
  }

  // region Convenience Accessors

  /**
   * Get module node by name, or null if not in graph.
   */
  fun getModule(name: ContentModuleName): ContentModuleNode? {
    val id = getCurrentStore().nodeId(name.value, NODE_CONTENT_MODULE)
    return if (id >= 0) ContentModuleNode(id) else null
  }

  // endregion

  // region Plugin Queries (for validation)

  /**
   * Get plugin dependencies declared in plugin.xml.
   *
   * Traverses: Plugin --dependsOnPlugin--> Plugin
   *
   * @param pluginName The plugin target name
   * @param includeOptional Whether to include optional legacy <depends> entries
   * @return Set of plugin IDs this plugin depends on (empty if none or plugin missing)
   */
  fun getPluginDependencies(pluginName: TargetName, includeOptional: Boolean = false): Set<PluginId> {
    val s = getCurrentStore()
    val pluginId = s.nodeId(pluginName.value, NODE_PLUGIN)
    if (pluginId < 0) return emptySet()

    val result = HashSet<PluginId>()
    s.successors(EDGE_PLUGIN_XML_DEPENDS_ON_PLUGIN, pluginId)?.forEach { packedEntry ->
      if (!includeOptional && unpackPluginDepOptional(packedEntry)) {
        return@forEach
      }
      result.add(s.pluginId(unpackNodeId(packedEntry)))
    }
    return result
  }

  // endregion

}

// endregion

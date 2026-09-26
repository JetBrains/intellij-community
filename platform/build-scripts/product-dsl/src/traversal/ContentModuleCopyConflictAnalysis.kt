// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.productLayout.traversal

import androidx.collection.MutableIntSet
import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.ContentModuleNode
import com.intellij.platform.pluginGraph.ContentWithNamespaceEdgeInvoker
import com.intellij.platform.pluginGraph.EDGE_CONTAINS_CONTENT_WITH_NAMESPACE
import com.intellij.platform.pluginGraph.GraphScope
import com.intellij.platform.pluginGraph.PluginId
import com.intellij.platform.pluginGraph.PluginModuleId
import com.intellij.platform.pluginGraph.PluginNode
import com.intellij.platform.pluginGraph.ProductNode
import com.intellij.platform.pluginGraph.TargetName
import com.intellij.platform.pluginGraph.contentName
import com.intellij.platform.pluginGraph.toActualId
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue

/** The longest path this analysis reconstructs. It only guards against a broken hop chain. */
private const val MAX_PATH_LENGTH: Int = 64

/**
 * One embedded copy of a duplicated content module name, plus the path that reaches the copy.
 *
 * The path starts at the content module that sees the copy. Each later step names the next hop.
 * The last step names the plugin that owns the copy.
 */
internal data class ContentModuleCopyOwner(
  val plugin: TargetName,
  /** The runtime ID of the copy. A missing namespace becomes an implicit per-plugin namespace. */
  val moduleId: PluginModuleId,
  @JvmField val path: List<String>,
)

/** A content module that reaches two or more embedded copies of the same content module name. */
internal data class ContentModuleCopyConflict(
  /** The content module that sees more than one copy. */
  val module: ContentModuleName,
  /** The content module name that more than one bundled plugin declares as embedded content. */
  val duplicatedModule: ContentModuleName,
  /** The reachable copies. This list always holds two entries or more. */
  @JvmField val owners: List<ContentModuleCopyOwner>,
)

/**
 * Finds content modules of [product] that reach two embedded copies of one content module name.
 *
 * The analysis works in three steps.
 * First it collects the content module names that two or more bundled plugins declare as embedded content.
 * Such a name is rare, which keeps the rest of the work small.
 * Then it walks the reverse dependency edges from each owning plugin.
 * Last it reports every content module that appears in the reverse set of two owners.
 *
 * The walk models the runtime class visibility of the plugin system.
 * A content module sees the main classloader of its own plugin.
 * A content module also sees the classloader of each module dependency and of each plugin dependency.
 * A plugin main classloader sees the classloader of each plugin dependency and of each module dependency.
 * A reverse walk from the owning plugin therefore finds every module that sees an embedded copy.
 *
 * The analysis reads an embedded declaration only. A declaration that is not embedded is not a copy here.
 * The reason is the graph, which keys a content module by name and holds one node per name.
 * An embedded copy lives in the main classloader of the owning plugin, so a walk from that plugin decides it.
 * A copy that is not embedded has its own classloader, and only the shared name node leads to it.
 * A walk from that node must expand it to reach a consumer, and an expansion crosses owners by construction.
 * Such a walk gives each owner the same path, which is proof that it cannot tell the copies apart.
 * The rule can therefore decide the embedded case only, and it states no verdict on the other one.
 * A dependency can also name a plugin by an alias, so the walk takes the alias edge as an extra hop.
 *
 * The graph keeps one node per content module name, so a node can stand for more than one runtime module.
 * The rule therefore needs three sets, and they answer three different questions. Do not merge them.
 *
 * The candidate set answers "what is reportable". A name belongs to it when two or more bundled plugins
 * declare an embedded copy.
 *
 * The expansion test answers "can a reference resolve the copy that the walk stands on". This is a question
 * about the walk, not about the node, so it is a test and not a set. Read `canExpand` for it.
 *
 * The seer set answers "does this node stand for one module".
 * A name that two or more bundled plugins declare stands for more than one, whatever the loading mode.
 * Such a node can never be the module that sees a copy.
 * The rule uses the union of that set and the candidate set. A narrower candidate rule then cannot widen it.
 *
 * These three sets coincided once, and treating them as one produced a defect four times.
 * The count of reports is NOT monotone in the candidate set: a smaller candidate set can produce MORE reports.
 * The walk is bounded by the expansion test, not by the candidate set.
 * A name that leaves the candidate set therefore stops bounding the walk, unless the expansion test stops it.
 *
 * The candidate set and the seer set read the bundled production plugins of the product.
 * A test plugin declaration is out of scope, and counting one would change every answer.
 * The expansion test reads a wider input: the product's own content and its module sets.
 * A module that the product ships outside any plugin is resolvable by name as well.
 *
 * The candidate set counts an embedded declaration and ignores the namespace.
 * A reported name can therefore carry a namespace on both declarations, with no private declaration at all.
 * Do not describe a report as a conflict between private copies.
 *
 * The cost is O(duplicated names x owners x graph). The reverse walk result is cached per plugin.
 *
 * @param modulePluginDependencies the `<plugin id="..."/>` dependencies of each content module descriptor
 */
internal fun GraphScope.analyzeContentModuleCopyConflicts(
  product: ProductNode,
  modulePluginDependencies: Map<ContentModuleName, List<PluginId>>,
): List<ContentModuleCopyConflict> {
  val declarations = collectContentDeclarations(product)
  if (declarations.candidateCopies.isEmpty()) {
    return emptyList()
  }

  val index = buildProductVisibilityIndex(product, modulePluginDependencies)
  val pluginReachCache = HashMap<Int, ReverseReach>()
  val conflicts = ArrayList<ContentModuleCopyConflict>()
  for ((moduleNodeId, copies) in declarations.candidateCopies) {
    // content module node ID -> indexes into `copies`
    val seenBy = HashMap<Int, MutableList<Int>>()
    val reachPerCopy = ArrayList<ReverseReach>(copies.size)
    for ((copyIndex, copy) in copies.withIndex()) {
      val pluginReach = pluginReachCache.getOrPut(copy.plugin.id) {
        reverseReach(index, declarations, startPlugin = copy.plugin.id)
      }
      reachPerCopy.add(pluginReach)
      for (seer in pluginReach.moduleHops.keys) {
        // The seer set answers a question about the node: does this node stand for one module?
        // A name that two or more bundled plugins declare stands for more than one, so it is never a seer.
        // The walk records such a node through `containsContent`, so the guard has to run here.
        if (declarations.nonSeerNameNodes.contains(seer)) {
          continue
        }
        seenBy.computeIfAbsent(seer) { ArrayList(2) }.add(copyIndex)
      }
    }

    val duplicatedModule = ContentModuleNode(moduleNodeId).contentName()
    for ((seer, copyIndexes) in seenBy) {
      if (copyIndexes.size < 2) {
        continue
      }
      val owners = copyIndexes.map { copyIndex ->
        val copy = copies.get(copyIndex)
        ContentModuleCopyOwner(
          plugin = copy.plugin.name(),
          moduleId = copy.moduleId,
          path = buildVisibilityPath(reachPerCopy.get(copyIndex), seer),
        )
      }
      conflicts.add(ContentModuleCopyConflict(
        module = ContentModuleNode(seer).contentName(),
        duplicatedModule = duplicatedModule,
        owners = owners.sortedWith(compareBy({ it.plugin.value }, { it.moduleId })),
      ))
    }
  }

  conflicts.sortWith(compareBy({ it.duplicatedModule.value }, { it.module.value }))
  return conflicts
}

/** One embedded declaration of a content module name by one bundled plugin. */
private class ContentModuleCopy(
  val plugin: PluginNode,
  val moduleId: PluginModuleId,
)

/**
 * The content declarations of the bundled plugins of one product, in the three forms the rule needs.
 *
 * The graph holds one content module node per name, so a node ID is the name key.
 * Each set answers its own question. Read [analyzeContentModuleCopyConflicts] for why they are not one set.
 */
private class ContentDeclarationIndex(
  /** Candidate name node ID -> the embedded copies. Two or more distinct plugins, so it is reportable. */
  @JvmField val candidateCopies: Map<Int, List<ContentModuleCopy>>,
  /**
   * A node in here is never a seer, because it does not stand for exactly one runtime module.
   *
   * It is the union of two sets. The ambiguous names, which two or more bundled plugins declare whatever the
   * loading mode and whatever the namespace. And the candidate names, which is a union member so that a
   * narrower candidate rule can never widen this set.
   *
   * Privacy plays no part here. Do not read this set as "the private names".
   *
   * The union is equal to the ambiguous set today, and that is safe rather than lucky.
   * A candidate needs two distinct plugins with an embedded declaration, so it has two distinct declarers.
   * It is therefore already ambiguous.
   * The union stays a union, so a later change to the candidate rule cannot break the equality unnoticed.
   */
  @JvmField val nonSeerNameNodes: MutableIntSet,
  /** Plugin node ID -> the name nodes that this plugin declares with no namespace, so privately. */
  @JvmField val privateContentByPlugin: Map<Int, MutableIntSet>,
  /**
   * Name node IDs that a reference can resolve from anywhere in the product.
   *
   * A name gets in through any of three routes. A bundled plugin declares it with a namespace. The product
   * declares it as direct content with a namespace. A module set of the product holds it, and a module set is
   * always shared: `ModuleSetBuilder.module` states that `buildModuleSetXml` emits one
   * `<content namespace="jetbrains">` block, so a module set cannot hold a private module.
   *
   * A product spec CAN hold a private module, through `ProductModulesContentSpecBuilder.privateModule`, so
   * product content is read with its namespace and not assumed to be shared.
   */
  @JvmField val resolvableNameNodes: MutableIntSet,
) {
  /** Whether [plugin] declares [moduleNodeId] with no namespace. */
  fun isPrivateContentOf(plugin: Int, moduleNodeId: Int): Boolean {
    return privateContentByPlugin.get(plugin)?.contains(moduleNodeId) == true
  }

  /** Whether a reference to this name resolves a copy somewhere in the product. */
  fun isResolvableByName(moduleNodeId: Int): Boolean = resolvableNameNodes.contains(moduleNodeId)
}

/**
 * Reads every content declaration of the bundled plugins once, and derives the three sets from it.
 *
 * The scope is the bundled production plugins of the product, which is the scope the report uses.
 * A test plugin declaration is out of scope, and counting it would change the answer.
 */
private fun GraphScope.collectContentDeclarations(product: ProductNode): ContentDeclarationIndex {
  val embeddedCopies = HashMap<Int, MutableList<ContentModuleCopy>>()
  val declarers = HashMap<Int, MutableIntSet>()
  val privateContentByPlugin = HashMap<Int, MutableIntSet>()
  val resolvableNameNodes = MutableIntSet()

  // A module set is always shared, so every module it holds is resolvable by name.
  product.includesModuleSet { moduleSet ->
    moduleSet.modulesRecursive { module -> resolvableNameNodes.add(module.id) }
  }

  // Direct product content carries a namespace of its own, because a product spec can declare a private
  // module. The DSL exposes no namespace accessor on a product node, so read the namespace edge directly.
  ContentWithNamespaceEdgeInvoker.create(EDGE_CONTAINS_CONTENT_WITH_NAMESPACE, product.id).invoke { declared, _ ->
    val declaredId = declared.moduleId()
    if (declaredId.namespace != null) {
      contentModule(declaredId.contentName())?.let { resolvableNameNodes.add(it.id) }
    }
  }

  product.bundles { plugin ->
    val pluginId = plugin.pluginIdOrNull
    plugin.containsContentWithNamespace { declared, loading ->
      val declaredId = declared.moduleId()
      val module = contentModule(declaredId.contentName()) ?: return@containsContentWithNamespace

      declarers.computeIfAbsent(module.id) { MutableIntSet() }.add(plugin.id)
      if (declaredId.namespace == null) {
        privateContentByPlugin.computeIfAbsent(plugin.id) { MutableIntSet() }.add(module.id)
      }
      else {
        resolvableNameNodes.add(module.id)
      }

      if (loading == ModuleLoadingRuleValue.EMBEDDED) {
        embeddedCopies.computeIfAbsent(module.id) { ArrayList(2) }.add(ContentModuleCopy(
          plugin = plugin,
          moduleId = if (pluginId == null) declaredId else declaredId.toActualId(pluginId),
        ))
      }
    }
  }

  val candidateCopies = HashMap<Int, List<ContentModuleCopy>>()
  for ((moduleNodeId, copies) in embeddedCopies) {
    if (copies.distinctBy { it.plugin.id }.size >= 2) {
      candidateCopies.put(moduleNodeId, copies)
    }
  }

  // The ambiguous names: a node that two or more bundled plugins declare stands for more than one module.
  val ambiguousNameNodes = MutableIntSet()
  for ((moduleNodeId, plugins) in declarers) {
    if (plugins.size >= 2) {
      ambiguousNameNodes.add(moduleNodeId)
    }
  }

  // Take the union with the candidate names, not the ambiguous set alone. A candidate needs two embedded
  // declarers, so it has two declarers, so the union should already equal the ambiguous set. The union holds
  // whether or not that reasoning is right, which is why it is a union and not a claim.
  val nonSeerNameNodes = MutableIntSet()
  nonSeerNameNodes.addAll(ambiguousNameNodes)
  for (moduleNodeId in candidateCopies.keys) {
    nonSeerNameNodes.add(moduleNodeId)
  }

  return ContentDeclarationIndex(
    candidateCopies = candidateCopies,
    nonSeerNameNodes = nonSeerNameNodes,
    privateContentByPlugin = privateContentByPlugin,
    resolvableNameNodes = resolvableNameNodes,
  )
}

/** The reverse edges of one product, in the form the reverse walk needs. */
private class ProductVisibilityIndex(
  /** Content module node IDs that the product ships. */
  @JvmField val modules: MutableIntSet,
  /** Plugin node IDs that the product bundles. */
  @JvmField val bundledPlugins: MutableIntSet,
  /** Content module node ID -> content modules that declare a dependency on it. */
  @JvmField val moduleConsumers: Map<Int, List<Int>>,
  /** Content module node ID -> bundled plugins that declare a `<module name="..."/>` dependency on it. */
  @JvmField val pluginModuleConsumers: Map<Int, List<Int>>,
  /** Plugin ID -> content modules that declare a `<plugin id="..."/>` dependency on it. */
  @JvmField val modulePluginConsumers: Map<PluginId, List<Int>>,
)

private fun GraphScope.buildProductVisibilityIndex(
  product: ProductNode,
  modulePluginDependencies: Map<ContentModuleName, List<PluginId>>,
): ProductVisibilityIndex {
  val modules = MutableIntSet()
  val bundledPlugins = MutableIntSet()
  product.containsContent { module, _ -> modules.add(module.id) }
  product.includesModuleSet { moduleSet ->
    moduleSet.modulesRecursive { module -> modules.add(module.id) }
  }
  product.bundles { plugin ->
    bundledPlugins.add(plugin.id)
    plugin.containsContent { module, _ -> modules.add(module.id) }
  }

  val moduleConsumers = HashMap<Int, MutableList<Int>>()
  val modulePluginConsumers = HashMap<PluginId, MutableList<Int>>()
  modules.forEach { moduleNodeId ->
    val module = ContentModuleNode(moduleNodeId)
    module.dependsOn { dependency ->
      if (modules.contains(dependency.id)) {
        moduleConsumers.computeIfAbsent(dependency.id) { ArrayList() }.add(moduleNodeId)
      }
    }
    for (pluginId in modulePluginDependencies.get(module.contentName()).orEmpty()) {
      modulePluginConsumers.computeIfAbsent(pluginId) { ArrayList() }.add(moduleNodeId)
    }
  }

  val pluginModuleConsumers = HashMap<Int, MutableList<Int>>()
  product.bundles { plugin ->
    plugin.dependsOnContentModule { module ->
      if (modules.contains(module.id)) {
        pluginModuleConsumers.computeIfAbsent(module.id) { ArrayList() }.add(plugin.id)
      }
    }
  }

  return ProductVisibilityIndex(
    modules = modules,
    bundledPlugins = bundledPlugins,
    moduleConsumers = moduleConsumers,
    pluginModuleConsumers = pluginModuleConsumers,
    modulePluginConsumers = modulePluginConsumers,
  )
}

/** One step of a reverse walk. [text] names the target of the step, so a path reads forwards. */
/**
 * How the walk arrived at a node. It fixes which copy of a name the walk stands on.
 *
 * The parent node kind is not enough. Three arrivals have a plugin as the parent, and only one of them means
 * that the plugin declares the node as its own content.
 */
private enum class ArrivalKind {
  /** Module from plugin: the plugin declares this module in its `<content>`, so the walk stands on that copy. */
  OWNED_CONTENT,
  /** Module from plugin: the module declares `<plugin id="..."/>` on that plugin, or on an alias of it. */
  PLUGIN_DEPENDENCY,
  /** Module from module: the consumer declares `<module name="..."/>` on the parent. */
  MODULE_DEPENDENCY,
  /** Plugin from plugin: the consumer plugin declares `<plugin id="..."/>` on the parent. */
  PLUGIN_ON_PLUGIN,
  /** Plugin from plugin: the parent plugin declares this node as one of its alias IDs. */
  PLUGIN_ALIAS,
  /** Plugin from module: the plugin declares `<module name="..."/>` on the parent. */
  PLUGIN_ON_MODULE,
  ;

  /** Whether the parent node of this arrival is a plugin. Derived, so the arrival stays the only source. */
  val parentIsPlugin: Boolean
    get() = this == OWNED_CONTENT || this == PLUGIN_DEPENDENCY || this == PLUGIN_ON_PLUGIN || this == PLUGIN_ALIAS
}

private class ReverseHop(
  @JvmField val arrival: ArrivalKind,
  @JvmField val parentId: Int,
  @JvmField val text: String,
) {
  val parentIsPlugin: Boolean
    get() = arrival.parentIsPlugin
}

/** The nodes that a reverse walk found, with the hop that the walk used to reach each node. */
private class ReverseReach(
  @JvmField val moduleHops: Map<Int, ReverseHop>,
  @JvmField val pluginHops: Map<Int, ReverseHop>,
)

/**
 * Walks the reverse class visibility edges from one start node.
 *
 * The walk starts at [startPlugin] and finds every content module that sees its main classloader.
 *
 * The walk stays inside the product. It skips a content module that the product does not ship.
 * It also skips a plugin that the product does not bundle.
 * A product bundles an alias node for each alias of each bundled plugin, so an alias hop stays inside too.
 *
 * The walk expands a content module only when a reference can resolve the copy that the walk stands on.
 * [canExpand] holds that test. Read [analyzeContentModuleCopyConflicts] for the rule behind it.
 */
private fun GraphScope.reverseReach(
  index: ProductVisibilityIndex,
  declarations: ContentDeclarationIndex,
  startPlugin: Int,
): ReverseReach {
  val moduleHops = HashMap<Int, ReverseHop>()
  val pluginHops = HashMap<Int, ReverseHop>()
  val visitedModules = MutableIntSet()
  val visitedPlugins = MutableIntSet()
  val moduleQueue = ArrayDeque<Int>()
  val pluginQueue = ArrayDeque<Int>()
  visitedPlugins.add(startPlugin)
  pluginQueue.add(startPlugin)

  while (pluginQueue.isNotEmpty() || moduleQueue.isNotEmpty()) {
    while (pluginQueue.isNotEmpty()) {
      val pluginNodeId = pluginQueue.removeFirst()
      val plugin = PluginNode(pluginNodeId)
      val pluginId = plugin.pluginIdOrNull
      val ownedText = "is a content module of plugin ${plugin.name().value}"
      val dependsText = "declares <plugin id=\"${pluginId?.value ?: plugin.name().value}\">"

      // every content module of the plugin sees the main classloader of the plugin
      plugin.containsContent { module, _ ->
        if (index.modules.contains(module.id) && visitedModules.add(module.id)) {
          moduleHops.put(module.id, ReverseHop(ArrivalKind.OWNED_CONTENT, parentId = pluginNodeId, text = ownedText))
          moduleQueue.add(module.id)
        }
      }
      // a plugin that declares a dependency on this plugin
      plugin.requiredByPlugin { consumer ->
        if (index.bundledPlugins.contains(consumer.id) && visitedPlugins.add(consumer.id)) {
          pluginHops.put(consumer.id, ReverseHop(ArrivalKind.PLUGIN_ON_PLUGIN, parentId = pluginNodeId, text = dependsText))
          pluginQueue.add(consumer.id)
        }
      }
      // a content module that declares a dependency on this plugin
      if (pluginId != null) {
        for (consumer in index.modulePluginConsumers.get(pluginId).orEmpty()) {
          if (visitedModules.add(consumer)) {
            moduleHops.put(consumer, ReverseHop(ArrivalKind.PLUGIN_DEPENDENCY, parentId = pluginNodeId, text = dependsText))
            moduleQueue.add(consumer)
          }
        }
      }
      // an alias of this plugin, which a dependency can name instead of the plugin ID
      val aliasText = "is an alias of plugin ${plugin.name().value}"
      plugin.declaresAlias { alias ->
        if (index.bundledPlugins.contains(alias.id) && visitedPlugins.add(alias.id)) {
          pluginHops.put(alias.id, ReverseHop(ArrivalKind.PLUGIN_ALIAS, parentId = pluginNodeId, text = aliasText))
          pluginQueue.add(alias.id)
        }
      }
    }

    while (moduleQueue.isNotEmpty()) {
      val moduleNodeId = moduleQueue.removeFirst()
      if (!canExpand(declarations, moduleHops.get(moduleNodeId), moduleNodeId)) {
        continue
      }

      val moduleName = ContentModuleNode(moduleNodeId).contentName().value
      val dependsText = "depends on module $moduleName"
      val declaresText = "declares <module name=\"$moduleName\">"

      for (consumer in index.moduleConsumers.get(moduleNodeId).orEmpty()) {
        if (visitedModules.add(consumer)) {
          moduleHops.put(consumer, ReverseHop(ArrivalKind.MODULE_DEPENDENCY, parentId = moduleNodeId, text = dependsText))
          moduleQueue.add(consumer)
        }
      }
      for (consumer in index.pluginModuleConsumers.get(moduleNodeId).orEmpty()) {
        if (visitedPlugins.add(consumer)) {
          pluginHops.put(consumer, ReverseHop(ArrivalKind.PLUGIN_ON_MODULE, parentId = moduleNodeId, text = declaresText))
          pluginQueue.add(consumer)
        }
      }
    }
  }

  return ReverseReach(moduleHops = moduleHops, pluginHops = pluginHops)
}

/**
 * Whether the walk may expand a content module, meaning whether a reference from outside can resolve
 * the copy that the walk stands on.
 *
 * The routing reads [ReverseHop.arrival], not the parent node kind. Three arrivals have a plugin as the
 * parent, and only [ArrivalKind.OWNED_CONTENT] means that the plugin declares the module as its own content.
 *
 * [ArrivalKind.OWNED_CONTENT]: the walk stands on the copy that this plugin declares.
 * A reference from outside the plugin resolves that copy only when the plugin gave it a namespace.
 * A reference from inside the plugin needs no expansion, because `containsContent` already found that module.
 *
 * Every other arrival: the walk stands on the copy that a reference to the name resolves.
 * A `<plugin id="..."/>` arrival is in this group, because the named plugin need not declare the name at all.
 * An alias arrival is in it for the same reason, and an alias node declares no content of its own.
 *
 * A node with no hop is the start of the walk, which is always a plugin, so a module always has a hop.
 */
private fun canExpand(declarations: ContentDeclarationIndex, hop: ReverseHop?, moduleNodeId: Int): Boolean {
  if (hop != null && hop.arrival == ArrivalKind.OWNED_CONTENT) {
    return !declarations.isPrivateContentOf(hop.parentId, moduleNodeId)
  }
  return declarations.isResolvableByName(moduleNodeId)
}

/** Rebuilds the path from one content module to the start of the reverse walk. */
private fun GraphScope.buildVisibilityPath(reach: ReverseReach, moduleNodeId: Int): List<String> {
  val path = ArrayList<String>()
  path.add(ContentModuleNode(moduleNodeId).contentName().value)
  var isPlugin = false
  var current = moduleNodeId
  while (path.size < MAX_PATH_LENGTH) {
    val hop = (if (isPlugin) reach.pluginHops else reach.moduleHops).get(current) ?: break
    path.add(hop.text)
    isPlugin = hop.parentIsPlugin
    current = hop.parentId
  }
  return path
}

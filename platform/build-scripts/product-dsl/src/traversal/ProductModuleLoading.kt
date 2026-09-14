// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.productLayout.traversal

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.ModuleSetNode
import com.intellij.platform.pluginGraph.PluginGraph
import com.intellij.platform.pluginGraph.PluginId
import com.intellij.platform.pluginGraph.PluginNode
import com.intellij.platform.pluginGraph.TargetName
import com.intellij.platform.pluginGraph.contentName
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleVisibilityValue
import org.jetbrains.intellij.build.productLayout.ProductModulesContentSpec
import org.jetbrains.intellij.build.productLayout.TestPluginSpec
import org.jetbrains.intellij.build.productLayout.buildContentBlocksAndChainMapping
import org.jetbrains.intellij.build.productLayout.collectAndValidateAliases
import org.jetbrains.intellij.build.productLayout.contentName
import org.jetbrains.intellij.build.productLayout.dependency.ModuleDescriptorCache
import org.jetbrains.intellij.build.productLayout.deps.ContentModuleDependencyPlanOutput
import org.jetbrains.intellij.build.productLayout.deps.PluginDependencyPlanOutput
import org.jetbrains.intellij.build.productLayout.deps.TestPluginDependencyPlanOutput
import org.jetbrains.intellij.build.productLayout.discovery.PluginContentInfo

/** The active content modules and the reasons that exclude other candidates from a product. */
internal data class ProductModuleLoadingResult(
  @JvmField val activationPaths: Map<ContentModuleName, List<String>>,
  @JvmField val exclusions: Map<ContentModuleName, String>,
)

/** Computes activation from the product graph and the effective descriptor dependency plans. */
internal class ProductModuleLoading(
  private val graph: PluginGraph,
  private val contentPlans: ContentModuleDependencyPlanOutput,
  pluginPlans: PluginDependencyPlanOutput,
  private val descriptorLookup: (ContentModuleName) -> ModuleDescriptorCache.DescriptorInfo?,
  private val pluginLookup: (TargetName) -> PluginContentInfo? = { null },
  private val testPluginPlans: TestPluginDependencyPlanOutput = TestPluginDependencyPlanOutput(emptyList()),
) {
  private val pluginPlansByName = pluginPlans.plans.associateBy { it.pluginContentModuleName.value }

  fun analyze(
    productName: String,
    spec: ProductModulesContentSpec? = null,
    additionalPlugins: List<TargetName> = emptyList(),
    disabledPluginIds: Set<PluginId> = emptySet(),
    testPlugins: List<TestPluginSpec> = emptyList(),
  ): ProductModuleLoadingResult {
    val content = spec?.let { buildContentBlocksAndChainMapping(it, collectModuleSetAliases = true) }
    val productAliases = if (content == null) emptySet() else collectAndValidateAliases(spec, content.aliasToSource).toSet()
    val selectedTestPlugins = testPlugins.associateBy { it.pluginId }
    return graph.query {
      val product = requireNotNull(product(productName)) { "Product '$productName' is absent from the plugin graph." }
      val candidates = ArrayList<Candidate>()
      val modules = LinkedHashMap<ContentModuleName, MutableList<Candidate>>()
      val plugins = LinkedHashMap<PluginId, MutableList<Candidate>>()
      val pluginNodes = LinkedHashMap<PluginNode, Candidate>()
      val descriptorPlugins = LinkedHashMap<TargetName, Pair<Candidate, PluginContentInfo>>()
      val coreModules = LinkedHashMap<ContentModuleName, Candidate>()

      fun addModule(name: ContentModuleName, loading: ModuleLoadingRuleValue, parent: Candidate?): Candidate {
        val candidate = Candidate(name.value, name, parent, loading)
        candidates.add(candidate)
        modules.getOrPut(name, ::ArrayList).add(candidate)
        for (alias in descriptorLookup(name)?.pluginAliases.orEmpty()) {
          plugins.getOrPut(PluginId(alias), ::ArrayList).add(candidate)
        }
        return candidate
      }

      fun addCoreModule(name: ContentModuleName, loading: ModuleLoadingRuleValue) {
        val existing = coreModules.get(name)
        if (existing == null) {
          coreModules.put(name, addModule(name, loading, parent = null))
        }
        else {
          existing.loading = loading
        }
      }

      val visitedSets = HashSet<ModuleSetNode>()
      fun addModuleSet(moduleSet: ModuleSetNode) {
        if (!visitedSets.add(moduleSet)) return
        moduleSet.nestedSet { addModuleSet(it) }
        moduleSet.containsModule { module, loading -> addCoreModule(module.name(), loading) }
      }
      if (content == null) {
        product.includesModuleSet { addModuleSet(it) }
        product.containsContent { module, loading -> addCoreModule(module.name(), loading) }
      }
      else {
        for (block in content.contentBlocks) {
          for (module in block.modules) addCoreModule(module.contentName(), module.loading)
        }
      }

      fun addPlugin(plugin: PluginNode) {
        val testSpec = selectedTestPlugins.get(plugin.pluginIdOrNull)
        if (plugin.isTest && testSpec == null || plugin in pluginNodes || plugin.pluginIdOrNull in disabledPluginIds) return
        val loading = if (plugin.isAlias) ModuleLoadingRuleValue.ON_DEMAND else ModuleLoadingRuleValue.REQUIRED
        val candidate = Candidate("plugin ${plugin.name().value}", moduleName = null, parent = null, loading = loading)
        candidates.add(candidate)
        pluginNodes.put(plugin, candidate)
        plugin.pluginIdOrNull?.let { plugins.getOrPut(it, ::ArrayList).add(candidate) }
        plugin.declaresAlias { alias ->
          alias.pluginIdOrNull?.let { plugins.getOrPut(it, ::ArrayList).add(candidate) }
        }
        fun addContent(name: ContentModuleName, loading: ModuleLoadingRuleValue) {
          val content = addModule(name, loading, candidate)
          content.dependencies.add(Dependency(candidate.label, listOf(candidate)))
          if (loading == ModuleLoadingRuleValue.REQUIRED || loading == ModuleLoadingRuleValue.EMBEDDED) {
            candidate.dependencies.add(Dependency(content.label, listOf(content)))
          }
        }
        if (testSpec == null) {
          plugin.containsContent { module, loading -> addContent(module.name(), loading) }
        }
        else {
          for (block in buildContentBlocksAndChainMapping(testSpec.spec).contentBlocks) {
            for (module in block.modules) addContent(module.contentName(), module.loading)
          }
        }
      }
      product.bundles { addPlugin(it) }
      for (testPlugin in testPlugins) {
        addPlugin(requireNotNull(plugin(testPlugin.pluginId.value)) { "Test plugin '${testPlugin.pluginId.value}' is absent from the graph." })
      }
      val suppliedPlugins = additionalPlugins + testPlugins.flatMap { it.additionalBundledPluginTargetNames }
      for (name in suppliedPlugins.distinct()) {
        val node = plugin(name.value)
        if (node != null) {
          addPlugin(node)
          continue
        }
        val info = requireNotNull(pluginLookup(name)) { "Bundled plugin '${name.value}' has no descriptor." }
        if (info.isTestPlugin || info.pluginId in disabledPluginIds) continue
        val candidate = Candidate("plugin ${name.value}", null, null, ModuleLoadingRuleValue.REQUIRED)
        candidates.add(candidate)
        descriptorPlugins.put(name, candidate to info)
        for (alias in listOfNotNull(info.pluginId) + info.pluginAliases) {
          plugins.getOrPut(alias, ::ArrayList).add(candidate)
        }
        for (module in info.contentModules) {
          val loading = module.loadingMode ?: ModuleLoadingRuleValue.OPTIONAL
          val child = addModule(module.moduleId.contentName(), loading, candidate)
          child.dependencies.add(Dependency(candidate.label, listOf(candidate)))
          if (loading == ModuleLoadingRuleValue.REQUIRED || loading == ModuleLoadingRuleValue.EMBEDDED) {
            candidate.dependencies.add(Dependency(child.label, listOf(child)))
          }
        }
      }

      fun addModuleDependency(candidate: Candidate, dependency: ContentModuleName) {
        val isPrivate = descriptorLookup(dependency)?.moduleVisibility == ModuleVisibilityValue.PRIVATE
        val sources = modules.get(dependency).orEmpty().filter { source ->
          source.parent == null || source.parent === candidate.parent || source.parent === candidate || !isPrivate
        }
        candidate.dependencies.add(Dependency(dependency.value, sources))
      }

      fun addPluginDependency(candidate: Candidate, dependency: PluginId) {
        candidate.dependencies.add(Dependency("plugin ${dependency.value}", plugins.get(dependency).orEmpty()))
      }

      for ((name, sources) in modules) {
        val dependencies = LinkedHashSet<ContentModuleName>()
        val plan = contentPlans.plansByModule.get(name)
        if (plan == null) {
          dependencies.addAll(descriptorLookup(name)?.existingModuleDependencies.orEmpty().map(::ContentModuleName))
          contentModule(name)?.dependsOn { dependencies.add(it.name()) }
        }
        else {
          dependencies.addAll(plan.moduleDependencies)
          plan.existingXmlModuleDependencies.filterTo(dependencies) { it in plan.suppressedModules }
        }
        val pluginDependencies = plan?.writtenPluginDependencies
                                 ?: descriptorLookup(name)?.existingPluginDependencies.orEmpty().map(::PluginId)
        for (candidate in sources) {
          dependencies.forEach { addModuleDependency(candidate, it) }
          pluginDependencies.forEach { addPluginDependency(candidate, it) }
        }
      }

      for ((name, entry) in descriptorPlugins) {
        val (candidate, info) = entry
        val plan = pluginPlansByName.get(name.value)
        val moduleDependencies = plan?.let { it.moduleDependencies + it.preserveExistingModuleDependencies + it.xiIncludeModuleDeps }
                                 ?: info.moduleDependencies
        val pluginDependencies = plan?.let { it.pluginDependencies + it.preserveExistingPluginDependencies + it.xiIncludePluginDeps }
                                 ?: info.pluginDependencies
        moduleDependencies.forEach { addModuleDependency(candidate, it) }
        pluginDependencies.forEach { addPluginDependency(candidate, it) }
        info.legacyDepends.filterNot { it.optional }.forEach { addPluginDependency(candidate, it.pluginId) }
      }

      for ((plugin, candidate) in pluginNodes) {
        val testSpec = selectedTestPlugins.get(plugin.pluginIdOrNull)
        if (testSpec != null) {
          testSpec.platformModule?.let { addModuleDependency(candidate, ContentModuleName(it)) }
          val testPlan = testPluginPlans.plansByPluginId.get(testSpec.pluginId)
          if (testPlan != null) {
            testPlan.moduleDependencies.filterNot { name -> modules.get(name).orEmpty().any { it.parent === candidate } }
              .forEach { addModuleDependency(candidate, it) }
            testPlan.pluginDependencies.forEach { addPluginDependency(candidate, it) }
            continue
          }
        }
        if (plugin.isAlias) {
          if (plugin.pluginIdOrNull in productAliases) continue
          val moduleProviders = plugins.get(plugin.pluginIdOrNull).orEmpty().filter { it.moduleName != null }
          if (moduleProviders.isNotEmpty()) candidate.dependencies.add(Dependency(candidate.label, moduleProviders))
          val providers = ArrayList<Candidate>()
          var declared = false
          plugin.aliasDeclaredByPlugin { provider ->
            declared = true
            pluginNodes.get(provider)?.let(providers::add)
          }
          if (declared) candidate.dependencies.add(Dependency("provider of ${candidate.label}", providers))
          continue
        }
        val plan = pluginPlansByName.get(plugin.name().value)
        if (plan == null) {
          plugin.dependsOnContentModule { addModuleDependency(candidate, it.name()) }
          plugin.dependsOnPlugin { dependency ->
            if (!dependency.isOptional) {
              addPluginDependency(candidate, dependency.target().pluginIdOrNull ?: PluginId(dependency.target().name().value))
            }
          }
        }
        else {
          val moduleDependencies = plan.moduleDependencies + plan.preserveExistingModuleDependencies + plan.xiIncludeModuleDeps
          moduleDependencies.distinct().forEach { addModuleDependency(candidate, it) }
          val pluginDependencies = plan.pluginDependencies + plan.preserveExistingPluginDependencies + plan.xiIncludePluginDeps
          pluginDependencies.distinct().forEach { addPluginDependency(candidate, it) }
          plugin.dependsOnPlugin { dependency ->
            if (!dependency.isOptional && dependency.hasLegacyFormat) {
              addPluginDependency(candidate, dependency.target().pluginIdOrNull ?: PluginId(dependency.target().name().value))
            }
          }
        }
      }

      resolve(candidates, modules)
    }
  }
}

private class Candidate(
  val label: String,
  val moduleName: ContentModuleName?,
  val parent: Candidate?,
  var loading: ModuleLoadingRuleValue,
) {
  val dependencies = ArrayList<Dependency>()
  var exclusion: String? = null
}

private class Dependency(val label: String, val candidates: List<Candidate>)

private fun resolve(
  candidates: List<Candidate>,
  modules: Map<ContentModuleName, List<Candidate>>,
): ProductModuleLoadingResult {
  var paths: Map<Candidate, List<String>>
  do {
    var changed: Boolean
    do {
      changed = false
      for (candidate in candidates) {
        if (candidate.exclusion != null) continue
        val unresolved = candidate.dependencies.firstOrNull { dependency -> dependency.candidates.none { it.exclusion == null } }
                         ?: continue
        val cause = unresolved.candidates.firstOrNull()?.exclusion ?: "absent or not visible in this product"
        candidate.exclusion = "${candidate.label} -> ${unresolved.label}: $cause"
        changed = true
      }
    }
    while (changed)

    paths = activationPaths(candidates)
    var removed = false
    for (candidate in candidates) {
      if (candidate.exclusion == null && candidate !in paths) {
        candidate.exclusion = "No active consumer requires ${candidate.label}."
        removed = true
      }
    }
  }
  while (removed)

  val activeModules = LinkedHashMap<ContentModuleName, List<String>>()
  val exclusions = LinkedHashMap<ContentModuleName, String>()
  val consumers = HashMap<Candidate, MutableList<Candidate>>()
  for (candidate in candidates) {
    for (dependency in candidate.dependencies) {
      dependency.candidates.forEach { consumers.getOrPut(it, ::ArrayList).add(candidate) }
    }
  }
  for ((name, sources) in modules) {
    val path = sources.mapNotNull { paths.get(it) }.minByOrNull { it.size }
    if (path == null) {
      val reasons = sources.mapNotNull { it.exclusion }.distinct().joinToString("; ")
      val excludedConsumers = sources.flatMap { consumers.get(it).orEmpty() }
        .mapNotNull { it.exclusion }
        .distinct()
        .sorted()
      exclusions.put(name, (listOf(reasons) + excludedConsumers).joinToString("\n      "))
    }
    else {
      activeModules.put(name, path)
    }
  }
  return ProductModuleLoadingResult(activeModules, exclusions)
}

private fun activationPaths(candidates: List<Candidate>): Map<Candidate, List<String>> {
  val paths = LinkedHashMap<Candidate, List<String>>()
  val queue = ArrayDeque<Candidate>()
  for (candidate in candidates) {
    if (candidate.exclusion == null && candidate.loading != ModuleLoadingRuleValue.ON_DEMAND) {
      paths.put(candidate, listOf(candidate.label))
      queue.add(candidate)
    }
  }
  while (queue.isNotEmpty()) {
    val candidate = queue.removeFirst()
    for (dependency in candidate.dependencies) {
      for (target in dependency.candidates) {
        if (target.exclusion == null && target !in paths) {
          paths.put(target, paths.getValue(candidate) + target.label)
          queue.add(target)
        }
      }
    }
  }
  return paths
}

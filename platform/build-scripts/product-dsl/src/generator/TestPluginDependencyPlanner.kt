// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout.generator

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.DependencyClassification
import com.intellij.platform.pluginGraph.PluginGraph
import com.intellij.platform.pluginGraph.PluginId
import com.intellij.platform.pluginGraph.TargetName
import org.jetbrains.intellij.build.productLayout.LIB_MODULE_PREFIX
import org.jetbrains.intellij.build.productLayout.TestPluginSpec
import org.jetbrains.intellij.build.productLayout.buildContentBlocksAndChainMapping
import org.jetbrains.intellij.build.productLayout.deps.ContentModuleDependencyPlan
import org.jetbrains.intellij.build.productLayout.deps.DependencyResolutionContext
import org.jetbrains.intellij.build.productLayout.deps.TestPluginDependencyPlan
import org.jetbrains.intellij.build.productLayout.deps.TestPluginDependencyPlanOutput
import org.jetbrains.intellij.build.productLayout.deps.TestPluginUnresolvedDependency
import org.jetbrains.intellij.build.productLayout.deps.buildAllowedMissingByModule
import org.jetbrains.intellij.build.productLayout.deps.collectResolvableModules
import org.jetbrains.intellij.build.productLayout.deps.resolveAllowedMissingPluginIds
import org.jetbrains.intellij.build.productLayout.model.error.DslTestPluginOwner
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.DataSlot
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode
import org.jetbrains.intellij.build.productLayout.pipeline.Slots

/**
 * Computes dependency plans for DSL-defined test plugins.
 *
 * This consolidates dependency resolution logic into a single step that
 * both the test plugin XML generator and validator consume.
 */
internal object TestPluginDependencyPlanner : PipelineNode {
  override val id get() = NodeIds.TEST_PLUGIN_DEPENDENCY_PLAN
  override val requires: Set<DataSlot<*>> get() = setOf(Slots.CONTENT_MODULE_PLAN)
  override val produces: Set<DataSlot<*>> get() = setOf(Slots.TEST_PLUGIN_DEPENDENCY_PLAN)

  override suspend fun execute(ctx: ComputeContext) {
    val model = ctx.model
    val productClassByName = model.discovery.products
      .associate { product -> product.name to (product.properties?.javaClass?.name ?: "test-product") }
    val testPluginsWithSource = model.dslTestPluginsByProduct.flatMap { (productName, specs) ->
      if (specs.isEmpty()) return@flatMap emptyList()
      val productClass = productClassByName[productName] ?: "test-product"
      specs.map { Triple(it, productClass, productName) }
    }

    if (testPluginsWithSource.isEmpty()) {
      ctx.publish(Slots.TEST_PLUGIN_DEPENDENCY_PLAN, TestPluginDependencyPlanOutput(plans = emptyList()))
      return
    }

    val depsByModule = ctx.get(Slots.CONTENT_MODULE_PLAN).plansByModule
    val pluginTargetNamesByPluginId = buildPluginTargetNamesByPluginId(model.pluginGraph)
    val pluginIdByTargetName = buildPluginIdByTargetName(model.pluginGraph)
    val resolutionContext = DependencyResolutionContext(model.pluginGraph)
    val allRealProductNames = embeddedCheckProductNames(model.discovery.products.map { it.name })

    val plans = testPluginsWithSource.map { (spec, productClass, productName) ->
      buildTestPluginDependencyPlan(
        graph = model.pluginGraph,
        resolutionContext = resolutionContext,
        spec = spec,
        productName = productName,
        productClass = productClass,
        depsByModule = depsByModule,
        pluginTargetNamesByPluginId = pluginTargetNamesByPluginId,
        pluginIdByTargetName = pluginIdByTargetName,
        allRealProductNames = allRealProductNames,
        dependencyChains = model.dslTestPluginDependencyChains[spec.pluginId].orEmpty(),
      )
    }

    ctx.publish(Slots.TEST_PLUGIN_DEPENDENCY_PLAN, TestPluginDependencyPlanOutput(plans))
  }
}

private data class TargetDependencyPlan(
  val moduleDependencies: Set<ContentModuleName>,
  val pluginDependencies: Set<PluginId>,
  val unresolvedDependencies: List<TestPluginUnresolvedDependency>,
)

private fun buildTestPluginDependencyPlan(
  graph: PluginGraph,
  resolutionContext: DependencyResolutionContext,
  spec: TestPluginSpec,
  productName: String,
  productClass: String,
  depsByModule: Map<ContentModuleName, ContentModuleDependencyPlan>,
  pluginTargetNamesByPluginId: Map<PluginId, Set<TargetName>>,
  pluginIdByTargetName: Map<TargetName, PluginId>,
  allRealProductNames: Set<String>,
  dependencyChains: Map<ContentModuleName, List<ContentModuleName>>,
): TestPluginDependencyPlan {
  val embeddedCheckProductNames = if (productName in allRealProductNames) setOf(productName) else allRealProductNames
  val contentData = buildContentBlocksAndChainMapping(spec.spec, collectModuleSetAliases = false)
  val contentModules = contentData.contentBlocks
    .flatMap { it.modules }
    .mapTo(LinkedHashSet()) { it.name }
  val allowedMissingByModule = buildAllowedMissingByModule(contentData)
  val globalAllowedMissing = spec.allowedMissingPluginIds.toSet()

  val bundledPluginNames = resolutionContext.resolveBundledPlugins(productName)
  val resolvableOwners = resolutionContext.resolveBundledPlugins(productName, spec.additionalBundledPluginTargetNames.toSet())
  val resolvableModules = collectResolvableModules(graph, productName, spec.additionalBundledPluginTargetNames.toSet())

  val requiredByPlugin = LinkedHashMap<PluginId, LinkedHashSet<ContentModuleName>>()
  val moduleDepsFromContent = LinkedHashSet<ContentModuleName>()
  val unresolvedPluginsFromContent = LinkedHashMap<PluginId, LinkedHashSet<TargetName>>()
  for (moduleName in contentModules) {
    val moduleDeps = depsByModule.get(moduleName)?.testDependencies ?: continue
    if (moduleDeps.isEmpty()) continue

    for (dependency in moduleDeps) {
      if (dependency in contentModules) continue
      if (dependency.value.startsWith(LIB_MODULE_PREFIX)) {
        if (!shouldSkipEmbeddedDependency(graph, dependency, embeddedCheckProductNames)) {
          moduleDepsFromContent.add(dependency)
        }
        continue
      }

      val owningPlugins = resolutionContext.resolveOwningPlugins(dependency, includeTestSources = true)
      val owningProdPlugins = owningPlugins.filterNot { it.isTest }
      val moduleAllowedMissing = resolveAllowedMissingPluginIds(
        moduleName = moduleName,
        allowedMissingByModule = allowedMissingByModule,
        dependencyChains = dependencyChains,
        globalAllowedMissing = globalAllowedMissing,
      )
      if (owningProdPlugins.isEmpty()) {
        val pluginId = pluginIdByTargetName[TargetName(dependency.value)]
        if (pluginId != null) {
          if (pluginId == spec.pluginId) continue
          if (pluginId in moduleAllowedMissing) continue
          if (TargetName(dependency.value) in resolvableOwners) {
            requiredByPlugin.computeIfAbsent(pluginId) { LinkedHashSet() }.add(moduleName)
          }
          else {
            val targets = unresolvedPluginsFromContent.computeIfAbsent(pluginId) { LinkedHashSet() }
            val names = pluginTargetNamesByPluginId[pluginId].orEmpty()
            if (names.isEmpty()) {
              targets.add(TargetName(dependency.value))
            }
            else {
              targets.addAll(names)
            }
          }
          continue
        }
        if (dependency in resolvableModules) continue
        if (!shouldSkipEmbeddedDependency(graph, dependency, embeddedCheckProductNames)) {
          moduleDepsFromContent.add(dependency)
        }
        continue
      }

      val resolvableProdOwners = owningProdPlugins.filter { it.name in resolvableOwners }
      if (resolvableProdOwners.isEmpty()) {
        continue
      }

      for (owner in resolvableProdOwners) {
        if (owner.pluginId == spec.pluginId) continue
        if (owner.pluginId in moduleAllowedMissing) continue
        requiredByPlugin.computeIfAbsent(owner.pluginId) { LinkedHashSet() }.add(moduleName)
      }
    }
  }

  val targetPlan = collectTargetDependencies(
    graph = graph,
    resolutionContext = resolutionContext,
      spec = spec,
      bundledPluginNames = bundledPluginNames,
      pluginTargetNamesByPluginId = pluginTargetNamesByPluginId,
      embeddedCheckProductNames = embeddedCheckProductNames,
    )

  val computedPluginDependencies = LinkedHashSet<PluginId>().apply {
    addAll(targetPlan.pluginDependencies)
    addAll(requiredByPlugin.keys)
  }
  val computedModuleDependencies = LinkedHashSet<ContentModuleName>().apply {
    addAll(targetPlan.moduleDependencies)
    addAll(moduleDepsFromContent)
  }
  val filteredModuleDependencies = if (resolvableModules.isEmpty()) {
    computedModuleDependencies
  }
  else {
    val (libraryModules, regularModules) = computedModuleDependencies.partition { it.value.startsWith(LIB_MODULE_PREFIX) }
    val filteredRegular = regularModules.filterNotTo(LinkedHashSet()) { it in resolvableModules }
    LinkedHashSet<ContentModuleName>().apply {
      addAll(libraryModules)
      addAll(filteredRegular)
    }
  }

  val filteredRequiredByPlugin = requiredByPlugin.mapValues { it.value.toSet() }
  val mergedUnresolvedDependencies = if (unresolvedPluginsFromContent.isEmpty()) {
    targetPlan.unresolvedDependencies
  }
  else {
    val existingUnresolvedPluginIds = targetPlan.unresolvedDependencies
      .filterIsInstance<TestPluginUnresolvedDependency.Plugin>()
      .mapTo(HashSet()) { it.dependencyId }
    val additional = unresolvedPluginsFromContent
      .filterKeys { it !in existingUnresolvedPluginIds }
      .map { (pluginId, targets) -> TestPluginUnresolvedDependency.Plugin(pluginId, targets) }
    if (additional.isEmpty()) targetPlan.unresolvedDependencies else targetPlan.unresolvedDependencies + additional
  }

  return TestPluginDependencyPlan(
    spec = spec,
    productName = productName,
    productClass = productClass,
    pluginDependencies = computedPluginDependencies.sortedBy { it.value },
    moduleDependencies = filteredModuleDependencies.sortedBy { it.value },
    requiredByPlugin = filteredRequiredByPlugin,
    unresolvedDependencies = mergedUnresolvedDependencies,
  )
}

private fun collectTargetDependencies(
  graph: PluginGraph,
  resolutionContext: DependencyResolutionContext,
  spec: TestPluginSpec,
  bundledPluginNames: Set<TargetName>,
  pluginTargetNamesByPluginId: Map<PluginId, Set<TargetName>>,
  embeddedCheckProductNames: Set<String>,
): TargetDependencyPlan {
  val moduleDeps = LinkedHashSet<ContentModuleName>()
  val pluginDeps = LinkedHashSet<PluginId>()
  val contentModules = HashSet<ContentModuleName>()
  val expectedPluginId = spec.pluginId
  val additionalBundledPluginTargetNames = spec.additionalBundledPluginTargetNames

  val unresolvedModules = LinkedHashMap<ContentModuleName, LinkedHashSet<DslTestPluginOwner>>()
  val unresolvedPlugins = LinkedHashMap<PluginId, LinkedHashSet<TargetName>>()

  val resolvablePluginIds = graph.query {
    val ids = HashSet<PluginId>()
    plugins { plugin ->
      val pluginName = plugin.name()
      if (pluginName in bundledPluginNames || pluginName in additionalBundledPluginTargetNames) {
        if (!plugin.isTest) {
          val pluginId = plugin.pluginIdOrNull ?: return@plugins
          ids.add(pluginId)
        }
      }
    }
    ids
  }

  graph.query {
    plugins { plugin ->
      val declaredPluginId = plugin.pluginIdOrNull ?: return@plugins
      if (declaredPluginId != expectedPluginId) return@plugins
      else if (plugin.name().value != expectedPluginId.value) {
        return@plugins
      }

      plugin.containsContent { module, _ -> contentModules.add(module.contentName()) }
      plugin.containsContentTest { module, _ -> contentModules.add(module.contentName()) }

      plugin.mainTarget { target ->
        target.dependsOn { dep ->
          if (!dep.isProduction()) return@dependsOn
          when (val classification = classifyTarget(dep.targetId)) {
            is DependencyClassification.ModuleDep -> {
              if (classification.moduleName in contentModules) return@dependsOn
              if (classification.moduleName.value.startsWith(LIB_MODULE_PREFIX)) {
                moduleDeps.add(classification.moduleName)
                return@dependsOn
              }
              val owners = resolutionContext.resolveOwningPlugins(classification.moduleName)
              val owningProdPlugins = owners.filterNot { it.isTest }
              if (owningProdPlugins.isNotEmpty()) {
                val resolvableOwners = owningProdPlugins.filter {
                  it.name in bundledPluginNames || it.name in additionalBundledPluginTargetNames
                }
                if (resolvableOwners.isNotEmpty()) {
                  for (owner in resolvableOwners) {
                    if (owner.pluginId != expectedPluginId) {
                      pluginDeps.add(owner.pluginId)
                    }
                  }
                }
                else {
                  val unresolvedOwners = owningProdPlugins.filter { it.pluginId != expectedPluginId }
                  if (unresolvedOwners.isNotEmpty()) {
                    val owners = unresolvedModules.computeIfAbsent(classification.moduleName) { LinkedHashSet() }
                    for (owner in unresolvedOwners) {
                      owners.add(DslTestPluginOwner(targetName = owner.name, pluginId = owner.pluginId))
                    }
                  }
                }
                return@dependsOn
              }
              val depModuleId = contentModule(classification.moduleName)?.id ?: -1
              if (depModuleId >= 0 && shouldSkipEmbeddedPluginDependency(depModuleId, embeddedCheckProductNames)) return@dependsOn
              moduleDeps.add(classification.moduleName)
            }
            is DependencyClassification.PluginDep -> {
              val pluginId = classification.pluginId
              if (pluginId == expectedPluginId) return@dependsOn
              if (pluginId in resolvablePluginIds) {
                pluginDeps.add(pluginId)
              }
              else {
                val targets = unresolvedPlugins.computeIfAbsent(pluginId) { LinkedHashSet() }
                val names = pluginTargetNamesByPluginId[pluginId].orEmpty()
                targets.addAll(names)
              }
            }
            DependencyClassification.Skip -> {}
          }
        }
      }
    }
  }

  val unresolved = ArrayList<TestPluginUnresolvedDependency>()
  for ((moduleName, owners) in unresolvedModules) {
    unresolved.add(TestPluginUnresolvedDependency.ContentModule(moduleName, owners))
  }
  for ((pluginId, targets) in unresolvedPlugins) {
    unresolved.add(TestPluginUnresolvedDependency.Plugin(pluginId, targets))
  }

  return TargetDependencyPlan(
    moduleDependencies = moduleDeps,
    pluginDependencies = pluginDeps,
    unresolvedDependencies = unresolved,
  )
}

private fun buildPluginTargetNamesByPluginId(graph: PluginGraph): Map<PluginId, Set<TargetName>> {
  val result = HashMap<PluginId, LinkedHashSet<TargetName>>()
  graph.query {
    plugins { plugin ->
      val pluginId = plugin.pluginIdOrNull ?: return@plugins
      result.computeIfAbsent(pluginId) { LinkedHashSet() }.add(plugin.name())
    }
  }
  return result
}

private fun buildPluginIdByTargetName(graph: PluginGraph): Map<TargetName, PluginId> {
  val result = HashMap<TargetName, PluginId>()
  graph.query {
    plugins { plugin ->
      if (plugin.isTest) return@plugins
      val pluginId = plugin.pluginIdOrNull ?: return@plugins
      result[plugin.name()] = pluginId
    }
  }
  return result
}


private fun shouldSkipEmbeddedDependency(
  graph: PluginGraph,
  moduleName: ContentModuleName,
  allRealProductNames: Set<String>,
): Boolean {
  return graph.query {
    val depModuleId = contentModule(moduleName)?.id ?: return@query false
    shouldSkipEmbeddedPluginDependency(depModuleId, allRealProductNames)
  }
}

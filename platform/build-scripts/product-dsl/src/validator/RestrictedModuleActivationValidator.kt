// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginId
import com.intellij.platform.pluginGraph.TargetName
import org.jetbrains.intellij.build.productLayout.ModuleActivation
import org.jetbrains.intellij.build.productLayout.ModuleSet
import org.jetbrains.intellij.build.productLayout.ProductModulesContentSpec
import org.jetbrains.intellij.build.productLayout.TestPluginSpec
import org.jetbrains.intellij.build.productLayout.contentName
import org.jetbrains.intellij.build.productLayout.debug
import org.jetbrains.intellij.build.productLayout.model.error.RestrictedModuleActivationError
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.DataSlot
import org.jetbrains.intellij.build.productLayout.pipeline.GenerationModel
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode
import org.jetbrains.intellij.build.productLayout.pipeline.Slots
import org.jetbrains.intellij.build.productLayout.traversal.ProductModuleLoading
import org.jetbrains.intellij.build.productLayout.traversal.ProductModuleLoadingResult

/**
 * Checks that a restricted content module is active only where a [ModuleActivation] allows it.
 * Also checks that each required module of an applicable activation is active.
 */
internal object RestrictedModuleActivationValidator : PipelineNode {
  override val id get() = NodeIds.RESTRICTED_MODULE_ACTIVATION_VALIDATION
  override val requires: Set<DataSlot<*>>
    get() = setOf(Slots.CONTENT_MODULE_PLAN, Slots.PLUGIN_DEPENDENCY_PLAN, Slots.TEST_PLUGIN_DEPENDENCY_PLAN)

  override fun execute(ctx: ComputeContext) {
    val model = ctx.model
    val restricted = collectRestrictedModules(model)
    if (restricted.isEmpty()) {
      return
    }

    val loading = ProductModuleLoading(
      graph = model.pluginGraph,
      contentPlans = ctx.get(Slots.CONTENT_MODULE_PLAN),
      pluginPlans = ctx.get(Slots.PLUGIN_DEPENDENCY_PLAN),
      descriptorLookup = { model.descriptorCache.getOrAnalyze(it.value) },
      pluginLookup = { model.pluginContentCache.getOrExtract(it) },
      testPluginPlans = ctx.get(Slots.TEST_PLUGIN_DEPENDENCY_PLAN),
    )
    val grants = model.config.pluginModuleActivations
    for (product in model.discovery.products.sortedBy { it.name }) {
      val spec = product.spec
      val productActivations = spec?.moduleActivations.orEmpty()
      val compatiblePlugins = model.config.nonBundledPlugins.get(product.name).orEmpty().sortedBy { it.value }
      val grantingPlugins = LinkedHashMap<TargetName, ModuleActivation>()
      val plainPlugins = ArrayList<TargetName>()
      for (target in compatiblePlugins) {
        val grant = pluginIdOf(model, target)?.let { grants.get(it) }
        if (grant == null) plainPlugins.add(target) else grantingPlugins.put(target, grant)
      }
      // A grant that the product activation already covers adds nothing to check
      val productActivation = joinActivations(productActivations)
      val extendingPlugins = grantingPlugins.filterValues { grant ->
        !productActivation.required.containsAll(grant.required) || !productActivation.allowed.containsAll(grant.allowed)
      }.keys.toList()

      val scenarios = buildScenarios(spec, model.dslTestPluginsByProduct.get(product.name).orEmpty(), extendingPlugins, plainPlugins)
      debug(DEBUG_TAG) {
        "product=${product.name} mode=${product.productModeId} scenarios=${scenarios.map { it.name }} " +
        "granting=${grantingPlugins.keys.map { it.value }} extending=${extendingPlugins.map { it.value }} plain=${plainPlugins.size}"
      }
      for (scenario in scenarios) {
        fun analyze(additionalPlugins: List<TargetName>): ProductModuleLoadingResult {
          return loading.analyze(
            productName = product.name,
            spec = spec,
            additionalPlugins = product.bundledPluginModules + additionalPlugins,
            disabledPluginIds = scenario.disabledPluginIds,
            testPlugins = scenario.testPlugins,
            productModeId = product.productModeId,
            productModeExcludedModules = product.productModeExcludedModules,
          )
        }

        val result = analyze(scenario.additionalPlugins)
        val activation = effectiveActivation(productActivations, result, grants)
        val unexpected = LinkedHashMap<ContentModuleName, List<String>>()
        result.activationPaths.filterTo(unexpected) { (module, _) -> module in restricted && module !in activation.allowed }

        // Compatible plugins without a grant add no activation, so this run is useful only while a restricted module is prohibited.
        if (scenario.checkPlainPlugins && plainPlugins.isNotEmpty() && !activation.allowed.containsAll(restricted)) {
          val withPlainPlugins = analyze(scenario.additionalPlugins + plainPlugins)
          val allowed = effectiveActivation(productActivations, withPlainPlugins, grants).allowed
          for ((module, path) in withPlainPlugins.activationPaths) {
            if (module in restricted && module !in allowed) {
              unexpected.putIfAbsent(module, listOf("compatible plugins") + path)
            }
          }
        }

        val missing = activation.required.filterNot { it in result.activationPaths }.associateWith {
          result.exclusions.get(it) ?: "The module is absent from this product."
        }
        if (unexpected.isNotEmpty() || missing.isNotEmpty()) {
          ctx.emitError(RestrictedModuleActivationError(
            context = product.name + (scenario.name?.let { " ($it)" } ?: ""),
            productModeId = product.productModeId,
            productModeExcludedModules = product.productModeExcludedModules,
            unexpectedModules = unexpected,
            missingModules = missing,
          ))
        }
      }
    }
  }
}

private const val DEBUG_TAG = "restrictedActivation"

private class Scenario(
  @JvmField val name: String?,
  @JvmField val disabledPluginIds: Set<PluginId> = emptySet(),
  @JvmField val testPlugins: List<TestPluginSpec> = emptyList(),
  @JvmField val additionalPlugins: List<TargetName> = emptyList(),
  @JvmField val checkPlainPlugins: Boolean = true,
)

private class EffectiveActivation(@JvmField val required: Set<ContentModuleName>, @JvmField val allowed: Set<ContentModuleName>)

/**
 * A product with exclusive plugins gets one scenario per plugin, and the other exclusive plugins stay disabled.
 * Each scenario runs once more with each test plugin that opts in to the check.
 * Each compatible plugin with a grant gets its own scenario.
 */
private fun buildScenarios(
  spec: ProductModulesContentSpec?,
  dslTestPlugins: List<TestPluginSpec>,
  grantingPlugins: List<TargetName>,
  plainPlugins: List<TargetName>,
): List<Scenario> {
  val exclusivePlugins = spec?.exclusivePluginIds.orEmpty()
  val testPlugins = dslTestPlugins.filter { it.checkModuleActivation }
  return buildList {
    for (enabledPlugin in exclusivePlugins.ifEmpty { listOf(null) }) {
      val disabledPluginIds = exclusivePlugins.filterTo(LinkedHashSet()) { it != enabledPlugin }
      val baseName = enabledPlugin?.let { "with ${it.value}" }
      add(Scenario(baseName, disabledPluginIds))
      for (testPlugin in testPlugins) {
        val name = if (baseName == null) "with ${testPlugin.pluginId.value}" else "$baseName and ${testPlugin.pluginId.value}"
        add(Scenario(name, disabledPluginIds, testPlugins = listOf(testPlugin)))
      }
    }
    for (plugin in grantingPlugins) {
      add(Scenario("with compatible ${plugin.value}", additionalPlugins = plainPlugins + plugin, checkPlainPlugins = false))
    }
  }
}

private fun pluginIdOf(model: GenerationModel, target: TargetName): PluginId? {
  return model.pluginGraph.query { plugin(target.value)?.pluginIdOrNull } ?: model.pluginContentCache.getOrExtract(target)?.pluginId
}

/** Joins the product activations with the grants of the plugins in the configuration. */
private fun effectiveActivation(
  productActivations: List<ModuleActivation>,
  result: ProductModuleLoadingResult,
  grants: Map<PluginId, ModuleActivation>,
): EffectiveActivation {
  return joinActivations(productActivations + result.pluginIds.mapNotNull { grants.get(it) })
}

private fun joinActivations(activations: List<ModuleActivation>): EffectiveActivation {
  val required = LinkedHashSet<ContentModuleName>()
  val allowed = LinkedHashSet<ContentModuleName>()
  for (activation in activations) {
    required.addAll(activation.required)
    allowed.addAll(activation.allowed)
  }
  return EffectiveActivation(required, allowed)
}

/** Collects the restricted modules from all module sets, product specs, and test plugin specs. */
private fun collectRestrictedModules(model: GenerationModel): Set<ContentModuleName> {
  val result = LinkedHashSet<ContentModuleName>()
  val visitedSets = HashSet<String>()
  fun addModuleSet(moduleSet: ModuleSet) {
    if (!visitedSets.add(moduleSet.name)) {
      return
    }
    moduleSet.modules.filter { it.restricted }.mapTo(result) { it.contentName() }
    moduleSet.nestedSets.forEach(::addModuleSet)
  }

  fun addSpec(spec: ProductModulesContentSpec) {
    spec.moduleSets.forEach { addModuleSet(it.moduleSet) }
    spec.additionalModules.filter { it.restricted }.mapTo(result) { it.contentName() }
    spec.testPlugins.forEach { addSpec(it.spec) }
  }

  model.discovery.allModuleSets.forEach(::addModuleSet)
  model.discovery.products.forEach { product -> product.spec?.let(::addSpec) }
  model.dslTestPluginsByProduct.values.forEach { specs -> specs.forEach { addSpec(it.spec) } }
  return result
}

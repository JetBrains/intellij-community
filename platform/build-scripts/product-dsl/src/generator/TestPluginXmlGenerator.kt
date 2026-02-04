// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout.generator

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.DependencyClassification
import com.intellij.platform.pluginGraph.PluginGraph
import com.intellij.platform.pluginGraph.PluginId
import com.intellij.platform.pluginGraph.TargetName
import org.jetbrains.intellij.build.productLayout.LIB_MODULE_PREFIX
import org.jetbrains.intellij.build.productLayout.TestPluginSpec
import org.jetbrains.intellij.build.productLayout.generateTestPluginXml
import org.jetbrains.intellij.build.productLayout.model.error.DslTestPluginDependencyError
import org.jetbrains.intellij.build.productLayout.model.error.DslTestPluginDependencyKind
import org.jetbrains.intellij.build.productLayout.model.error.DslTestPluginOwner
import org.jetbrains.intellij.build.productLayout.model.error.ValidationError
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.DataSlot
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode
import org.jetbrains.intellij.build.productLayout.pipeline.Slots
import org.jetbrains.intellij.build.productLayout.pipeline.TestPluginsOutput
import org.jetbrains.intellij.build.productLayout.traversal.collectBundledPluginNames
import org.jetbrains.intellij.build.productLayout.traversal.collectOwningPlugins

/**
 * Generator for test plugin XML files.
 *
 * Generates `plugin.xml` files for test plugins defined in product specs.
 * Test plugins have simpler structure than products - metadata, dependencies, and content modules.
 *
 * **Input:** DSL test plugins from [org.jetbrains.intellij.build.productLayout.pipeline.GenerationModel.dslTestPluginsByProduct]
 * **Output:** Test plugin.xml files in test resources
 *
 * **Publishes:** [Slots.TEST_PLUGINS] with generation results
 *
 * **No dependencies** - can run immediately (level 0).
 *
 * @see org.jetbrains.intellij.build.productLayout.generateTestPluginXml
 */
internal object TestPluginXmlGenerator : PipelineNode {
  override val id get() = NodeIds.TEST_PLUGIN_XML
  override val produces: Set<DataSlot<*>> get() = setOf(Slots.TEST_PLUGINS)

  override suspend fun execute(ctx: ComputeContext) {
    val model = ctx.model
    // Collect test plugin specs with their source product class
    val testPluginsWithSource = model.discovery.products.flatMap { product ->
      val productClass = product.properties?.javaClass?.name ?: "test-product"
      val testPlugins = model.dslTestPluginsByProduct.get(product.name).orEmpty()
      testPlugins.map { Triple(it, productClass, product.name) }
    }

    if (testPluginsWithSource.isEmpty()) {
      ctx.publish(Slots.TEST_PLUGINS, TestPluginsOutput(files = emptyList()))
      return
    }

    val errors = ArrayList<ValidationError>()
    val results = testPluginsWithSource.map { (spec, productClass, productName) ->
      val deps = collectTestPluginDependencies(graph = model.pluginGraph, spec = spec, productName = productName, errors = errors)
      val moduleDependencyChains = model.dslTestPluginDependencyChains.get(spec.pluginId).orEmpty()
      generateTestPluginXml(
        spec = spec,
        productPropertiesClass = productClass,
        projectRoot = model.projectRoot,
        moduleDependencies = deps.moduleDependencies,
        pluginDependencies = deps.pluginDependencies,
        moduleDependencyChains = moduleDependencyChains,
        strategy = model.fileUpdater,
      )
    }

    if (errors.isNotEmpty()) {
      ctx.emitErrors(errors)
    }
    ctx.publish(Slots.TEST_PLUGINS, TestPluginsOutput(files = results))
  }
}

private data class TestPluginDependencies(
  val moduleDependencies: List<ContentModuleName>,
  val pluginDependencies: List<PluginId>,
)

private fun collectTestPluginDependencies(
  graph: PluginGraph,
  spec: TestPluginSpec,
  productName: String,
  errors: MutableList<ValidationError>,
): TestPluginDependencies {
  val moduleDeps = LinkedHashSet<ContentModuleName>()
  val pluginDeps = LinkedHashSet<PluginId>()
  val contentModules = HashSet<ContentModuleName>()
  val expectedPluginId = spec.pluginId
  val bundledPluginNames = collectBundledPluginNames(graph, productName)
  val additionalBundledPluginTargetNames = spec.additionalBundledPluginTargetNames
  val allowedMissingPluginIds = spec.allowedMissingPluginIds
  val pluginTargetNamesByPluginId = HashMap<PluginId, LinkedHashSet<TargetName>>()
  graph.query {
    plugins { plugin ->
      val pluginId = plugin.pluginIdOrNull ?: return@plugins
      pluginTargetNamesByPluginId.computeIfAbsent(pluginId) { LinkedHashSet() }.add(plugin.name())
    }
  }
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
              val owners = collectOwningPlugins(graph, classification.moduleName)
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
                  val disallowedOwners = unresolvedOwners.filterNot { it.pluginId in allowedMissingPluginIds }
                  if (disallowedOwners.isNotEmpty()) {
                    errors.add(
                      DslTestPluginDependencyError(
                        context = spec.pluginId.value,
                        testPluginId = spec.pluginId,
                        productName = productName,
                        dependencyKind = DslTestPluginDependencyKind.CONTENT_MODULE,
                        contentModuleDependencyId = classification.moduleName,
                        owningPlugins = disallowedOwners.mapTo(LinkedHashSet()) { DslTestPluginOwner(targetName = it.name, pluginId = it.pluginId) },
                      )
                    )
                  }
                }
                return@dependsOn
              }
              val depModuleId = contentModule(classification.moduleName)?.id ?: -1
              if (depModuleId >= 0 && shouldSkipEmbeddedPluginDependency(depModuleId)) return@dependsOn
              moduleDeps.add(classification.moduleName)
            }
            is DependencyClassification.PluginDep -> {
              val pluginId = classification.pluginId
              if (pluginId == expectedPluginId) return@dependsOn
              if (pluginId in resolvablePluginIds) {
                pluginDeps.add(pluginId)
              }
              else if (!allowedMissingPluginIds.contains(pluginId)) {
                errors.add(
                  DslTestPluginDependencyError(
                    context = spec.pluginId.value,
                    testPluginId = spec.pluginId,
                    productName = productName,
                    dependencyKind = DslTestPluginDependencyKind.PLUGIN,
                    pluginDependencyId = pluginId,
                    dependencyTargetNames = pluginTargetNamesByPluginId.get(pluginId) ?: emptySet(),
                  )
                )
              }
            }
            DependencyClassification.Skip -> {}
          }
        }
      }
    }
  }

  return TestPluginDependencies(
    moduleDependencies = moduleDeps.sortedBy { it.value },
    pluginDependencies = pluginDeps.sortedBy { it.value },
  )
}

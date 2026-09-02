// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginGraph
import com.intellij.platform.pluginGraph.PluginId
import com.intellij.platform.pluginGraph.TargetName
import com.intellij.platform.pluginGraph.contentName
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleVisibilityValue
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jetbrains.intellij.build.productLayout.config.SuppressionConfig
import org.jetbrains.intellij.build.productLayout.dependency.ModuleDescriptorCache
import org.jetbrains.intellij.build.productLayout.model.error.ContentModuleDependencyDeclarationError
import org.jetbrains.intellij.build.productLayout.model.error.ContentModuleDependencyProblem
import org.jetbrains.intellij.build.productLayout.model.error.ContentModuleDependencyProblemKind
import org.jetbrains.intellij.build.productLayout.model.error.ValidationError
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.DataSlot
import org.jetbrains.intellij.build.productLayout.pipeline.GenerationModel
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode
import org.jetbrains.intellij.build.productLayout.pipeline.Slots
import java.nio.file.Path

/** `com.intellij.modules.java` is the old alias of the Java plugin. */
private const val JAVA_MODULE_ID = "com.intellij.modules.java"

/** The IDE never loads K1, so a dependency on it stays silent. */
private const val K1_MODULE_ID = "com.intellij.modules.kotlin.k1"

/** Every plugin gets the platform, so the dependency adds nothing. */
private const val PLATFORM_MODULE_ID = "com.intellij.modules.platform"

/** The prefix of a platform module alias, such as `com.intellij.modules.lang`. */
private const val PLATFORM_MODULE_PREFIX = "com.intellij.modules."

/**
 * The prefix of an OS requirement id, such as `com.intellij.modules.os.mac`.
 *
 * It repeats `IdeaPluginOsRequirement.fromModuleId`. product-dsl has no dependency on `intellij.platform.core`,
 * so it cannot call that function.
 */
private const val OS_MODULE_PREFIX = "com.intellij.modules.os."

/** The escape hatch of the visibility rule. It keeps the name that `PluginModelValidator` used. */
private val visibilityCheckDisabled = System.getProperty("intellij.platform.plugin.modules.check.visibility") == "disabled"

/**
 * Content module dependency declaration validation.
 *
 * Purpose: Check the form of each `<dependencies>` entry of a content module descriptor.
 * The subject is the text of the descriptor, and not the module graph, so the rule runs once per descriptor
 * and not once per product.
 *
 * It reports six problems.
 * 1. `<plugin id="com.intellij.modules.java">` names the Java plugin with the old alias.
 * 2. `<plugin id="com.intellij.modules.platform">` is redundant next to another module dependency.
 * 3. No plugin and no alias in the monorepo defines the plugin id.
 * 4. The descriptor declares one plugin id two times.
 * 5. A `<module name="...">` element names the main module of a plugin.
 * 6. An `internal` content module is used from another namespace.
 *
 * A missing module and a module that no plugin declares as content belong to `ContentModuleDependencyValidator`.
 * A `private` module of another plugin belongs to the resolution query. A dependency of a required module on an
 * optional module of the same plugin belongs to `PluginDependencyResolution`.
 *
 * Inputs: `Slots.CONTENT_MODULE_PLAN` (for the order), the plugin graph, the descriptor cache, the plugin content
 * cache and the suppression config.
 * Output: `ContentModuleDependencyDeclarationError`.
 * Auto-fix: none.
 *
 * Glossary: docs/validators/README.md.
 * Spec: docs/validators/content-module-dependency-declaration.md.
 */
internal object ContentModuleDependencyDeclarationValidator : PipelineNode {
  override val id get() = NodeIds.CONTENT_MODULE_DEPENDENCY_DECLARATION_VALIDATION

  // Requires CONTENT_MODULE_PLAN, so the descriptor cache is warm and the graph holds every module dependency edge.
  override val requires: Set<DataSlot<*>> get() = setOf(Slots.CONTENT_MODULE_PLAN)

  override suspend fun execute(ctx: ComputeContext) {
    val model = ctx.model
    val facts = collectGraphFacts(model.pluginGraph)
    if (facts.owners.isEmpty()) {
      return
    }

    val descriptorData = readDescriptors(model = model, facts = facts)
    ctx.emitErrors(validateDescriptorDependencies(
      facts = facts,
      descriptorData = descriptorData,
      suppressionConfig = model.suppressionConfig,
      projectRoot = model.projectRoot,
    ))
  }
}

/** One `<content>` entry that names a content module. */
private class ContentOwner(
  val pluginId: PluginId?,
  /** The namespace of the `<content>` entry, or null when the entry declares none. */
  @JvmField val namespace: String?,
)

private class GraphFacts(
  /** A content module name to the plugins that declare it, with the namespace of each `<content>` entry. */
  @JvmField val owners: Map<ContentModuleName, List<ContentOwner>>,
  /** The plugin ids that a plugin main module defines. */
  @JvmField val definedPluginIds: Set<PluginId>,
  /** The plugin ids that an alias node carries. */
  @JvmField val aliasNodeIds: Set<PluginId>,
  /** A plugin main module name to the id of that plugin. */
  @JvmField val mainModuleToPluginId: Map<ContentModuleName, PluginId?>,
  /** The target of every plugin that has a main module. */
  @JvmField val pluginTargets: List<TargetName>,
)

private fun collectGraphFacts(graph: PluginGraph): GraphFacts = graph.query {
  val owners = HashMap<ContentModuleName, MutableList<ContentOwner>>()
  val definedPluginIds = HashSet<PluginId>()
  val aliasNodeIds = HashSet<PluginId>()
  val mainModuleToPluginId = HashMap<ContentModuleName, PluginId?>()
  val pluginTargets = ArrayList<TargetName>()

  plugins { plugin ->
    val pluginId = plugin.pluginIdOrNull
    if (plugin.isAlias) {
      if (pluginId != null) {
        aliasNodeIds.add(pluginId)
      }
    }
    if (plugin.hasMainTarget) {
      pluginTargets.add(plugin.name())
      mainModuleToPluginId.put(plugin.contentModuleName(), pluginId)
      if (pluginId != null) {
        definedPluginIds.add(pluginId)
      }
    }

    plugin.containsContentWithNamespace { module, _ ->
      val moduleId = module.moduleId()
      owners.computeIfAbsent(moduleId.contentName()) { ArrayList() }.add(ContentOwner(pluginId, moduleId.namespace))
    }
    plugin.containsContentWithNamespaceTest { module, _ ->
      val moduleId = module.moduleId()
      owners.computeIfAbsent(moduleId.contentName()) { ArrayList() }.add(ContentOwner(pluginId, moduleId.namespace))
    }
  }

  GraphFacts(
    owners = owners,
    definedPluginIds = definedPluginIds,
    aliasNodeIds = aliasNodeIds,
    mainModuleToPluginId = mainModuleToPluginId,
    pluginTargets = pluginTargets,
  )
}

private class DescriptorData(
  @JvmField val descriptors: Map<ContentModuleName, ModuleDescriptorCache.DescriptorInfo>,
  /** Every plugin alias of the monorepo, from a plugin descriptor and from a content module descriptor. */
  @JvmField val aliasIds: Set<PluginId>,
)

/**
 * Reads every content module descriptor and every plugin descriptor once.
 *
 * A plugin id resolves through an alias, and an alias comes from either kind of descriptor. So the pass must read
 * both kinds before the first check runs.
 */
private suspend fun readDescriptors(model: GenerationModel, facts: GraphFacts): DescriptorData {
  val moduleNames = facts.owners.keys.toList()
  return coroutineScope {
    val descriptorTasks = moduleNames.map { name -> async { model.descriptorCache.getOrAnalyze(name.value) } }
    val pluginTasks = facts.pluginTargets.map { target -> async { model.pluginContentCache.getOrExtract(target) } }

    val aliasIds = HashSet<PluginId>()
    val descriptors = HashMap<ContentModuleName, ModuleDescriptorCache.DescriptorInfo>(moduleNames.size)
    for ((index, task) in descriptorTasks.withIndex()) {
      val descriptor = task.await() ?: continue
      descriptors.put(moduleNames[index], descriptor)
      for (alias in descriptor.pluginAliases) {
        aliasIds.add(PluginId(alias))
      }
    }
    for (task in pluginTasks) {
      aliasIds.addAll(task.await()?.pluginAliases ?: emptyList())
    }

    DescriptorData(descriptors = descriptors, aliasIds = aliasIds)
  }
}

private fun validateDescriptorDependencies(
  facts: GraphFacts,
  descriptorData: DescriptorData,
  suppressionConfig: SuppressionConfig,
  projectRoot: Path,
): List<ValidationError> {
  val errors = ArrayList<ValidationError>()
  for ((moduleName, owners) in facts.owners) {
    val descriptor = descriptorData.descriptors.get(moduleName) ?: continue
    val problems = ArrayList<ContentModuleDependencyProblem>()
    checkPluginDependencies(
      moduleName = moduleName,
      owners = owners,
      descriptor = descriptor,
      facts = facts,
      descriptorData = descriptorData,
      suppressionConfig = suppressionConfig,
      problems = problems,
    )
    checkModuleDependencies(
      owners = owners,
      descriptor = descriptor,
      facts = facts,
      descriptorData = descriptorData,
      problems = problems,
    )
    if (problems.isEmpty()) {
      continue
    }

    errors.add(ContentModuleDependencyDeclarationError(
      context = moduleName.value,
      contentModuleName = moduleName,
      descriptorPath = relativizePath(projectRoot, descriptor.descriptorPath),
      problems = problems,
    ))
  }

  errors.sortBy { it.context }
  return errors
}

private fun checkPluginDependencies(
  moduleName: ContentModuleName,
  owners: List<ContentOwner>,
  descriptor: ModuleDescriptorCache.DescriptorInfo,
  facts: GraphFacts,
  descriptorData: DescriptorData,
  suppressionConfig: SuppressionConfig,
  problems: MutableList<ContentModuleDependencyProblem>,
) {
  // `com.intellij.modules.platform` counts itself, so a lone platform dependency keeps the count at one.
  val moduleDependencyCount = descriptor.existingModuleDependencies.size +
                              descriptor.existingPluginDependencies.count { it.startsWith(PLATFORM_MODULE_PREFIX) }
  val ownerPluginIds = owners.mapNotNullTo(HashSet()) { it.pluginId }
  // The set holds the explicit validationExceptions entry, plus the plugin ids that the generator must not add.
  val allowedMissing = suppressionConfig.getAllowedMissingPlugins(moduleName)
  val declared = HashSet<PluginId>()

  for (rawId in descriptor.existingPluginDependencies) {
    if (rawId == JAVA_MODULE_ID) {
      problems.add(ContentModuleDependencyProblem(
        kind = ContentModuleDependencyProblemKind.JAVA_MODULE_ALIAS,
        message = "the plugin dependency '$JAVA_MODULE_ID' uses the old alias of the Java plugin",
        fix = "<plugin id=\"com.intellij.java\"/>",
      ))
      continue
    }
    if (rawId == K1_MODULE_ID) {
      continue
    }
    if (rawId == PLATFORM_MODULE_ID) {
      // todo: remove this check when MP-7413 is fixed in the plugin verifier version that the Marketplace uses
      if (moduleDependencyCount > 1) {
        problems.add(ContentModuleDependencyProblem(
          kind = ContentModuleDependencyProblemKind.REDUNDANT_PLATFORM_DEPENDENCY,
          message = "the plugin dependency '$PLATFORM_MODULE_ID' is redundant next to another module dependency",
          fix = "remove the '$PLATFORM_MODULE_ID' element",
        ))
      }
      continue
    }

    val pluginId = PluginId(rawId)
    if (pluginId in ownerPluginIds) {
      //todo: uncomment and fix violations
      //problems.add(a dependency on the parent plugin)
      continue
    }

    if (!isPluginIdResolved(pluginId = pluginId, facts = facts, descriptorData = descriptorData, allowedMissing = allowedMissing)) {
      problems.add(ContentModuleDependencyProblem(
        kind = ContentModuleDependencyProblemKind.UNRESOLVED_PLUGIN,
        message = "no plugin defines the plugin id '$rawId'",
        fix = "fix the id, or add it to validationExceptions of '${moduleName.value}' in suppressions.json",
      ))
      continue
    }

    if (!declared.add(pluginId)) {
      problems.add(ContentModuleDependencyProblem(
        kind = ContentModuleDependencyProblemKind.DUPLICATE_PLUGIN,
        message = "the descriptor declares the plugin dependency '$rawId' two times",
        fix = "remove the second '$rawId' element",
      ))
    }
  }
}

private fun isPluginIdResolved(
  pluginId: PluginId,
  facts: GraphFacts,
  descriptorData: DescriptorData,
  allowedMissing: Set<PluginId>,
): Boolean {
  return pluginId in facts.definedPluginIds ||
         pluginId in facts.aliasNodeIds ||
         pluginId in descriptorData.aliasIds ||
         pluginId in allowedMissing ||
         pluginId.value.startsWith(OS_MODULE_PREFIX)
}

private fun checkModuleDependencies(
  owners: List<ContentOwner>,
  descriptor: ModuleDescriptorCache.DescriptorInfo,
  facts: GraphFacts,
  descriptorData: DescriptorData,
  problems: MutableList<ContentModuleDependencyProblem>,
) {
  for (rawName in descriptor.existingModuleDependencies) {
    val dependencyName = ContentModuleName(rawName)
    val dependencyOwners = facts.owners.get(dependencyName)
    if (dependencyOwners.isNullOrEmpty()) {
      // A module that no plugin declares as content belongs to `ContentModuleDependencyValidator`.
      // Only the plugin main module gets a report here, because the fix is a change of the element.
      if (facts.mainModuleToPluginId.containsKey(dependencyName)) {
        val pluginId = facts.mainModuleToPluginId.get(dependencyName)
        problems.add(ContentModuleDependencyProblem(
          kind = ContentModuleDependencyProblemKind.PLUGIN_AS_MODULE,
          message = "the module dependency '$rawName' names the main module of a plugin",
          fix = if (pluginId == null) "use a plugin element instead of a module element" else "<plugin id=\"${pluginId.value}\"/>",
        ))
      }
      continue
    }

    if (visibilityCheckDisabled) {
      continue
    }

    val dependencyDescriptor = descriptorData.descriptors.get(dependencyName) ?: continue
    if (dependencyDescriptor.moduleVisibility != ModuleVisibilityValue.INTERNAL) {
      continue
    }

    // The namespace of the referencing module is a property of the `<content>` entry, so each namespace of the
    // referencing module gets one report.
    for (owner in owners.distinctBy { it.namespace }) {
      val conflicting = dependencyOwners.firstOrNull { it.namespace != owner.namespace } ?: continue
      problems.add(ContentModuleDependencyProblem(
        kind = ContentModuleDependencyProblemKind.INTERNAL_FROM_OTHER_NAMESPACE,
        message = "the module dependency '$rawName' is internal ${describeNamespace(conflicting.namespace)}" +
                  " in the plugin '${describePlugin(conflicting.pluginId)}', and this module is ${describeNamespace(owner.namespace)}",
        fix = "use the 'public' visibility in '$rawName.xml', or use one namespace in both plugins",
      ))
    }
  }
}

private fun describeNamespace(namespace: String?): String {
  return if (namespace == null) "without a namespace" else "in the namespace '$namespace'"
}

private fun describePlugin(pluginId: PluginId?): String = pluginId?.value ?: "<no id>"

private fun relativizePath(projectRoot: Path, path: Path): String {
  val root = projectRoot.toAbsolutePath().normalize()
  val target = if (path.isAbsolute) path.normalize() else root.resolve(path).normalize()
  return try {
    root.relativize(target).toString().replace('\\', '/')
  }
  catch (_: IllegalArgumentException) {
    target.toString().replace('\\', '/')
  }
}

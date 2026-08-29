// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework.pluginModel

import com.intellij.platform.buildScripts.testFramework.distributionContent.PackagingCheckFailure
import com.intellij.platform.buildScripts.testFramework.distributionContent.PackagingTargetValidationContext
import com.intellij.platform.buildScripts.testFramework.distributionContent.PackagingTargetValidationSpec
import com.intellij.platform.buildScripts.testFramework.distributionContent.PackagingTargetValidationStage
import com.intellij.platform.runtime.product.ProductMode
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.intellij.build.mapConcurrent
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import org.opentest4j.MultipleFailuresError
import java.nio.file.Path
import kotlin.io.path.createDirectories

/**
 * One plugin-dependency validation of a packaging target.
 *
 * The default provider reads the layout the build computed, so the validation runs at the [PackagingTargetValidationStage.LAYOUT]
 * stage, beside the packaging of its target. [createPluginModuleDependencyValidationSpecs] pairs such a config with the
 * `plugin-layout-parity` validation, which keeps the layout and the packaged distribution in agreement.
 */
@Internal
class PluginModuleDependencyValidationConfig(
  /**
   * Must be equal to [com.intellij.platform.buildScripts.testFramework.distributionContent.PackagingTargetSpec.id] of some spec instance.
   * That spec is used to determine the content of the product which modules will be validated.
   */
  @JvmField val targetId: String,
  @JvmField val validationName: String = "plugin-module-dependencies",
  @JvmField val mainModuleOfCorePlugin: String,
  @JvmField val corePluginDescriptorPath: String = "META-INF/plugin.xml",
  @JvmField val options: PluginDependenciesValidationOptions,
  @JvmField val productMode: ProductMode = ProductMode.MONOLITH,
  /**
   * What the validation reads. A config whose provider reads the packaged content report must stay
   * [PackagingTargetValidationStage.CONTENT].
   */
  @JvmField val stage: PackagingTargetValidationStage = PackagingTargetValidationStage.LAYOUT,
  @JvmField val createPluginLayoutProvider: (PackagingTargetValidationContext) -> PluginLayoutProvider = { context ->
    createPluginLayoutProviderByDistributionState(
      context = context,
      mainModuleOfCorePlugin = mainModuleOfCorePlugin,
      corePluginDescriptorPath = corePluginDescriptorPath,
    )
  },
  /**
   * Returns the plugins that each validation variant leaves out.
   *
   * Some plugins conflict with each other, so one variant cannot validate them all.
   */
  @JvmField val pluginExclusionSubsets: (PackagingTargetValidationContext) -> PluginExclusionSubsets = { singleValidationVariant },
)

private data class ValidationVariant(
  @JvmField val index: Int,
  @JvmField val pluginExclusionSubset: Set<String>,
)

/**
 * The plugin-dependency validations of [configs], with the parity gate of every one that reads the build layout.
 *
 * A config that reads the build layout runs beside the packaging of its target. The gate is what keeps that sound, so
 * the list derives from the configs and never enumerates the targets a second time.
 */
@Internal
fun createPluginModuleDependencyValidationSpecs(configs: List<PluginModuleDependencyValidationConfig>): List<PackagingTargetValidationSpec> {
  return configs.map(::createPluginModuleDependencyValidationSpec) +
         configs.filter { it.stage == PackagingTargetValidationStage.LAYOUT }.map(::createPluginLayoutParityValidationSpec)
}

@Internal
fun createPluginModuleDependencyValidationSpec(config: PluginModuleDependencyValidationConfig): PackagingTargetValidationSpec {
  return PackagingTargetValidationSpec(
    targetId = config.targetId,
    name = config.validationName,
    problemMessage = "plugin module dependency validation problems",
    stage = config.stage,
    validator = { context -> validatePluginModuleDependencies(context = context, config = config) },
  )
}

/** The provider that reads the packaged content report of the target. A validation that uses it must run at the `CONTENT` stage. */
@Internal
fun createPluginLayoutProviderByContentReport(
  context: PackagingTargetValidationContext,
  mainModuleOfCorePlugin: String,
  corePluginDescriptorPath: String,
): PluginLayoutProvider {
  return createLayoutProviderByContentReport(
    content = context.content(),
    mainModuleOfCorePlugin = mainModuleOfCorePlugin,
    corePluginDescriptorPath = corePluginDescriptorPath,
    outputProvider = context.outputProvider,
  )
}

/** The provider that reads the layout the build computed for the target, before it packs a jar. */
@Internal
fun createPluginLayoutProviderByDistributionState(
  context: PackagingTargetValidationContext,
  mainModuleOfCorePlugin: String,
  corePluginDescriptorPath: String,
): PluginLayoutProvider {
  return createLayoutProviderByDistributionState(
    state = context.layout.distributionState,
    context = context.layout.buildContext,
    mainModuleOfCorePlugin = mainModuleOfCorePlugin,
    corePluginDescriptorPath = corePluginDescriptorPath,
  )
}

private fun validatePluginModuleDependencies(
  context: PackagingTargetValidationContext,
  config: PluginModuleDependencyValidationConfig,
): List<PackagingCheckFailure> {
  val pluginLayoutProvider = config.createPluginLayoutProvider(context)
  val exclusionSubsets = config.pluginExclusionSubsets(context)
  val variants = exclusionSubsets.subsets
    .mapIndexed { index, pluginExclusionSubset -> ValidationVariant(index = index, pluginExclusionSubset = pluginExclusionSubset) }
  val errors: List<PluginModuleConfigurationError> = when (variants.size) {
    0 -> emptyList()
    1 -> {
      validatePluginModuleDependencyVariant(
        context = context,
        config = config,
        pluginLayoutProvider = pluginLayoutProvider,
        variant = variants.single(),
        tempDir = context.tempDir,
      )
    }
    else -> {
      variants.mapConcurrent { variant ->
        validatePluginModuleDependencyVariant(
          context = context,
          config = config,
          pluginLayoutProvider = pluginLayoutProvider,
          variant = variant,
          tempDir = context.tempDir.resolve("variant-${variant.index}").createDirectories(),
        )
      }.flatten()
    }
  }
  return (errors + exclusionSubsets.toUncoveredPluginErrors()).distinctBy { it.toString() }.toPackagingCheckFailures()
}

/**
 * A plugin that no variant validates is a gap in the validation, so it becomes a failure.
 */
private fun PluginExclusionSubsets.toUncoveredPluginErrors(): List<PluginModuleConfigurationError> {
  return uncoveredPlugins.map { (mainModule, reason) ->
    PluginModuleConfigurationError(
      pluginModelModuleName = mainModule,
      errorMessage = """
        |No validation variant loads the plugin of '$mainModule', because $reason.
        |So nothing validates the module dependencies of that plugin.
        |Either resolve the conflict that keeps the plugin out, or raise the variant cap of this validation.
      """.trimMargin(),
    )
  }
}

private fun validatePluginModuleDependencyVariant(
  context: PackagingTargetValidationContext,
  config: PluginModuleDependencyValidationConfig,
  pluginLayoutProvider: PluginLayoutProvider,
  variant: ValidationVariant,
  tempDir: Path,
): List<PluginModuleConfigurationError> {
  return spanBuilder("plugin module dependency validation: ${config.targetId} ${config.validationName} variant ${variant.index}").use { span ->
    span.setAttribute("packaging.target.id", config.targetId)
    span.setAttribute("packaging.validation.name", config.validationName)
    span.setAttribute("plugin.dependency.validation.variant.index", variant.index.toLong())
    span.setAttribute("plugin.dependency.validation.ignored.plugin.count", variant.pluginExclusionSubset.size.toLong())
    if (variant.pluginExclusionSubset.isNotEmpty()) {
      span.setAttribute("plugin.dependency.validation.ignored.plugins", variant.pluginExclusionSubset.sorted().joinToString(","))
    }

    val options = if (variant.pluginExclusionSubset.isEmpty()) {
      config.options
    }
    else {
      config.options.copy(pluginsToIgnore = variant.pluginExclusionSubset)
    }
    PluginDependenciesValidator.validatePluginDependencies(
      project = context.project,
      productMode = config.productMode,
      pluginLayoutProvider = pluginLayoutProvider,
      tempDir = tempDir,
      options = options,
    )
  }
}

/** One failure per plugin model module, sorted by name. Several errors of one module fold into one [MultipleFailuresError]. */
private fun Collection<PluginModuleConfigurationError>.toPackagingCheckFailures(): List<PackagingCheckFailure> {
  return groupBy { it.pluginModelModuleName }
    .map { (name, errors) -> PackagingCheckFailure(name = name, error = errors.singleOrNull() ?: MultipleFailuresError("${errors.size} failures", errors)) }
    .sortedBy { it.name }
}

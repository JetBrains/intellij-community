// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.core.CoreBundle
import com.intellij.idea.AppMode
import com.intellij.ide.plugins.PluginDependencyAnalysis.DependencyRef
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.productMode.ProductMode
import com.intellij.util.PlatformUtils
import com.intellij.util.system.CpuArch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

@ApiStatus.Internal
class PluginLoadingProblem internal constructor(
  val plugin: IdeaPluginDescriptor,
  val shouldNotifyUser: Boolean,
  val disabledDependencyId: PluginId? = null,
  val kind: Kind = Kind.OTHER,
  val severity: Severity = Severity.UNEXPECTED,
  val productMode: ProductMode? = null,
  val dependencyPluginId: PluginId? = null,
  val dependency: DependencyRef? = null,
  private val detailedMessageSupplier: () -> @NlsContexts.DetailedDescription String,
  private val shortMessageSupplier: () -> @NlsContexts.Label String,
) {
  val detailedMessage: @NlsContexts.DetailedDescription String get() = detailedMessageSupplier()
  val shortMessage: @NlsContexts.Label String get() = shortMessageSupplier()
  val unavailableModuleId: PluginModuleId?
    get() = when (val dependency = dependency) {
      is DependencyRef.ContentModule -> dependency.moduleId
      is DependencyRef.Plugin, null -> null
    }

  @ApiStatus.Internal
  enum class Kind {
    OTHER,
    ULTIMATE_DISABLED,
    PRODUCT_MODE_MISMATCH,
  }

  @ApiStatus.Internal
  enum class Severity {
    EXPECTED,
    UNEXPECTED,
  }
}

@ApiStatus.Internal
fun DescriptorExclusionReason.toSelectionPluginNonLoadReason(): PluginLoadingProblem? {
  val plugin = descriptor.getMainDescriptor()
  return when (this) {
    is PluginIsIncompatibleWithProduct -> incompatibilityReason.convertToUIError(descriptor)
    is IncompatibleWithAnotherModule -> pluginIsIncompatibleWithAnotherPlugin(plugin, preferredIncompatibleModule, true)
    is PluginDeclaresConflictingId -> pluginIdConflictNonLoadReason(this)
    is PluginIsMarkedDisabled,
    is PluginVersionIsSuperseded -> null
    is ProductRulesImposedExclusion -> when (productReason) {
      is LegacyPluginIsCompatibleOnlyWithIntelliJIDEA -> pluginIsCompatibleOnlyWithIntelliJIDEA(plugin)
      is NonBundledPluginsLoadingIsDisabled -> nonBundledPluginsAreExplicitlyDisabled(plugin)
      is PluginIsNotContainedInTheExplicitlyConfiguredSubsetOfPluginsForLoading ->
        pluginIsNotRequiredForLoadingTheExplicitlyConfiguredSubsetOfPlugins(plugin)
      is PluginLoadingIsDisabledCompletelyExceptCore -> pluginLoadingIsDisabledCompletely(plugin)
      else -> null
    }
    else -> null
  }
}

/** temporary migration helper */
internal fun PluginIncompatibilityReason.convertToUIError(descriptor: IdeaPluginDescriptorImpl): PluginLoadingProblem {
  return when (this) {
    is PluginIncompatibilityReason.IncompatibleWithCpuArch -> pluginIsIncompatibleWithHostCpu(descriptor, requiredArch, hostArch)
    is PluginIncompatibilityReason.IncompatibleWithHostPlatform -> pluginIsIncompatibleWithHostPlatform(descriptor, requiredOS, hostOS.name)
    PluginIncompatibilityReason.MalformedSinceUntilConstraints -> pluginMalformedSinceUntilConstraints(descriptor)
    is PluginIncompatibilityReason.SinceBuildConstraintViolation -> pluginSinceBuildConstraintViolation(descriptor, productBuildNumber)
    is PluginIncompatibilityReason.UntilBuildConstraintViolation -> pluginUntilBuildConstraintViolation(descriptor, productBuildNumber)
    is PluginIncompatibilityReason.PluginIsMarkedBroken -> pluginIsMarkedBroken(descriptor)
  }
}

private fun pluginIdConflictNonLoadReason(reason: PluginDeclaresConflictingId): PluginLoadingProblem = PluginLoadingProblem(
  plugin = reason.descriptor,
  shouldNotifyUser = true,
  detailedMessageSupplier = {
    CoreBundle.message("plugin.loading.error.long.declares.conflicting.id", reason.descriptor.name, reason.conflictingId,
                       reason.conflictingModule.getMainDescriptor().name)
  },
  shortMessageSupplier = { CoreBundle.message("plugin.loading.error.short.declares.conflicting.id", reason.conflictingId) },
)

internal fun pluginDependencyIsDisabled(
  plugin: IdeaPluginDescriptor,
  dependencyId: PluginId,
  shouldNotifyUser: Boolean,
  dependencyDescriptor: IdeaPluginDescriptor? = null,
): PluginLoadingProblem = PluginLoadingProblem(
  plugin = plugin,
  shouldNotifyUser = shouldNotifyUser,
  disabledDependencyId = dependencyId,
  dependency = DependencyRef.Plugin(dependencyId),
  detailedMessageSupplier = {
    CoreBundle.message("plugin.loading.error.long.depends.on.disabled.plugin", plugin.name, dependencyDescriptor?.name ?: dependencyId.idString)
  },
  shortMessageSupplier = {
    CoreBundle.message("plugin.loading.error.short.depends.on.disabled.plugin", dependencyDescriptor?.name ?: dependencyId.idString)
  },
)

internal fun pluginRequiresDisabledUltimate(plugin: IdeaPluginDescriptor): PluginLoadingProblem = PluginLoadingProblem(
  plugin = plugin,
  kind = PluginLoadingProblem.Kind.ULTIMATE_DISABLED,
  severity = PluginLoadingProblem.Severity.EXPECTED,
  shouldNotifyUser = false,
  disabledDependencyId = PluginManagerCore.ULTIMATE_PLUGIN_ID,
  dependency = DependencyRef.Plugin(PluginManagerCore.ULTIMATE_PLUGIN_ID),
  detailedMessageSupplier = {
    CoreBundle.message("plugin.loading.error.long.depends.on.disabled.plugin", plugin.name, PluginManagerCore.ULTIMATE_PLUGIN_ID)
  },
  shortMessageSupplier = { CoreBundle.message("plugin.loading.error.short.depends.on.disabled.plugin", PluginManagerCore.ULTIMATE_PLUGIN_ID) },
)

internal fun pluginRequiresUnavailableProductModeModule(
  plugin: IdeaPluginDescriptor,
  moduleId: PluginModuleId,
  productMode: ProductMode,
): PluginLoadingProblem {
  val messageKey = when (productMode) {
    ProductMode.LIGHT_REMOTE, ProductMode.LIGHT_WITH_RD_CONNECTION, ProductMode.LIGHT_MONOLITH -> "plugin.loading.problem.product.mode.light"
    ProductMode.FRONTEND -> "plugin.loading.problem.product.mode.frontend"
    ProductMode.BACKEND -> "plugin.loading.problem.product.mode.backend"
    ProductMode.MONOLITH, ProductMode.LANGUAGE_SERVER -> "plugin.loading.problem.product.mode.unsupported"
  }
  return PluginLoadingProblem(
    plugin = plugin,
    kind = PluginLoadingProblem.Kind.PRODUCT_MODE_MISMATCH,
    severity = PluginLoadingProblem.Severity.EXPECTED,
    shouldNotifyUser = false,
    productMode = productMode,
    dependency = DependencyRef.ContentModule(moduleId),
    detailedMessageSupplier = { CoreBundle.message(messageKey) },
    shortMessageSupplier = { CoreBundle.message(messageKey) },
  )
}

internal fun nonBundledPluginsAreExplicitlyDisabled(plugin: IdeaPluginDescriptor): PluginLoadingProblem = PluginLoadingProblem(
  plugin = plugin,
  severity = PluginLoadingProblem.Severity.EXPECTED,
  shouldNotifyUser = false,
  detailedMessageSupplier = { CoreBundle.message("plugin.loading.error.long.custom.plugin.loading.disabled", plugin.name) },
  shortMessageSupplier = { CoreBundle.message("plugin.loading.error.short.custom.plugin.loading.disabled") },
)

internal fun pluginIsMarkedBroken(plugin: IdeaPluginDescriptor): PluginLoadingProblem = PluginLoadingProblem(
  plugin = plugin,
  shouldNotifyUser = true,
  detailedMessageSupplier = { CoreBundle.message("plugin.loading.error.long.marked.as.broken", plugin.name, plugin.version) },
  shortMessageSupplier = { CoreBundle.message("plugin.loading.error.short.marked.as.broken") },
)

internal fun pluginIsCompatibleOnlyWithIntelliJIDEA(plugin: IdeaPluginDescriptor): PluginLoadingProblem = PluginLoadingProblem(
  plugin = plugin,
  shouldNotifyUser = true,
  detailedMessageSupplier = { CoreBundle.message("plugin.loading.error.long.compatible.with.intellij.idea.only", plugin.name) },
  shortMessageSupplier = { CoreBundle.message("plugin.loading.error.short.compatible.with.intellij.idea.only") },
)

internal fun pluginIsIncompatibleWithHostPlatform(
  plugin: IdeaPluginDescriptor,
  requiredOs: IdeaPluginOsRequirement,
  hostOs: @NlsSafe String,
): PluginLoadingProblem = PluginLoadingProblem(
  plugin = plugin,
  shouldNotifyUser = true,
  detailedMessageSupplier = {
    CoreBundle.message("plugin.loading.error.long.incompatible.with.platform", plugin.name, plugin.version, requiredOs, hostOs)
  },
  shortMessageSupplier = { CoreBundle.message("plugin.loading.error.short.incompatible.with.platform", requiredOs) },
)

internal fun pluginIsIncompatibleWithHostCpu(
  plugin: IdeaPluginDescriptor,
  requiredCpuArch: PluginCpuArchRequirement,
  hostCpu: @NlsSafe CpuArch,
): PluginLoadingProblem = PluginLoadingProblem(
  plugin = plugin,
  shouldNotifyUser = true,
  detailedMessageSupplier = {
    CoreBundle.message("plugin.loading.error.long.incompatible.with.cpu", plugin.name, plugin.version, requiredCpuArch, hostCpu)
  },
  shortMessageSupplier = { CoreBundle.message("plugin.loading.error.short.incompatible.with.platform", requiredCpuArch) },
)

internal fun pluginSinceBuildConstraintViolation(
  plugin: IdeaPluginDescriptor,
  productBuildNumber: BuildNumber,
): PluginLoadingProblem = PluginLoadingProblem(
  plugin = plugin,
  shouldNotifyUser = true,
  detailedMessageSupplier = {
    buildCompatibilityDetailedMessage("plugin.loading.error.long.incompatible.since.build",
                                      "plugin.loading.error.long.incompatible.since.build.on.remote.dev.side",
                                      plugin,
                                      plugin.sinceBuild,
                                      productBuildNumber)
  },
  shortMessageSupplier = { CoreBundle.message("plugin.loading.error.short.incompatible.since.build", plugin.sinceBuild) },
)

internal fun pluginUntilBuildConstraintViolation(
  plugin: IdeaPluginDescriptor,
  productBuildNumber: BuildNumber,
): PluginLoadingProblem = PluginLoadingProblem(
  plugin = plugin,
  shouldNotifyUser = true,
  detailedMessageSupplier = {
    buildCompatibilityDetailedMessage("plugin.loading.error.long.incompatible.until.build",
                                      "plugin.loading.error.long.incompatible.until.build.on.remote.dev.side",
                                      plugin,
                                      plugin.untilBuild,
                                      productBuildNumber)
  },
  shortMessageSupplier = { CoreBundle.message("plugin.loading.error.short.incompatible.until.build", plugin.untilBuild) },
)

private fun buildCompatibilityDetailedMessage(
  regularMessageKey: @NonNls String,
  remoteDevMessageKey: @NonNls String,
  plugin: IdeaPluginDescriptor,
  requiredBuild: String?,
  productBuildNumber: BuildNumber,
): @NlsContexts.DetailedDescription String {
  val remoteDevSide = remoteDevPluginLoadingSide()
  return if (remoteDevSide == null) {
    CoreBundle.message(regularMessageKey, plugin.name, plugin.version, requiredBuild, productBuildNumber)
  }
  else {
    CoreBundle.message(remoteDevMessageKey, plugin.name, plugin.version, requiredBuild, productBuildNumber, remoteDevSide)
  }
}

private fun remoteDevPluginLoadingSide(): @Nls String? {
  return when {
    AppMode.isRemoteDevHost() -> CoreBundle.message("plugin.loading.error.remote.dev.side.backend")
    PlatformUtils.isJetBrainsClient() -> CoreBundle.message("plugin.loading.error.remote.dev.side.client")
    else -> null
  }
}

internal fun pluginMalformedSinceUntilConstraints(plugin: IdeaPluginDescriptor): PluginLoadingProblem = PluginLoadingProblem(
  plugin = plugin,
  shouldNotifyUser = true,
  detailedMessageSupplier = { CoreBundle.message("plugin.loading.error.long.failed.to.load.requirements.for.ide.version", plugin.name) },
  shortMessageSupplier = { CoreBundle.message("plugin.loading.error.short.failed.to.load.requirements.for.ide.version") },
)

internal fun pluginLoadingIsDisabledCompletely(plugin: IdeaPluginDescriptor): PluginLoadingProblem = PluginLoadingProblem(
  plugin = plugin,
  severity = PluginLoadingProblem.Severity.EXPECTED,
  shouldNotifyUser = false,
  detailedMessageSupplier = { CoreBundle.message("plugin.loading.error.long.plugin.loading.disabled", plugin.name) },
  shortMessageSupplier = { CoreBundle.message("plugin.loading.error.short.plugin.loading.disabled") },
)

internal fun pluginPackagePrefixConflict(
  plugin: PluginModuleDescriptor,
  module: PluginModuleDescriptor,
  conflictingModule: PluginModuleDescriptor,
): PluginLoadingProblem = PluginLoadingProblem(
  plugin = plugin,
  shouldNotifyUser = true,
  detailedMessageSupplier = {
    CoreBundle.message(
      "plugin.loading.error.long.package.prefix.conflict",
      plugin.getMainDescriptor().name,
      conflictingModule.getMainDescriptor().name,
      getPackagePrefixConflictModuleId(module),
      getPackagePrefixConflictNamespace(module),
      getPackagePrefixConflictModuleId(conflictingModule),
      getPackagePrefixConflictNamespace(conflictingModule),
      module.packagePrefix ?: conflictingModule.packagePrefix ?: "<none>",
    )
  },
  shortMessageSupplier = {
    CoreBundle.message("plugin.loading.error.short.package.prefix.conflict", plugin.getMainDescriptor().name,
                       conflictingModule.getMainDescriptor().name)
  },
)

private fun getPackagePrefixConflictModuleId(descriptor: PluginModuleDescriptor): String {
  return when (descriptor) {
    is PluginMainDescriptor -> descriptor.pluginId.idString
    is ContentModuleDescriptor -> descriptor.moduleId.name
  }
}

private fun getPackagePrefixConflictNamespace(descriptor: PluginModuleDescriptor): String {
  return when (descriptor) {
    is PluginMainDescriptor -> descriptor.implicitNamespaceForPluginDescriptorModule ?: "<none>"
    is ContentModuleDescriptor -> descriptor.moduleId.namespace
  }
}

internal fun pluginIsIncompatibleWithAnotherPlugin(
  plugin: IdeaPluginDescriptor,
  incompatiblePlugin: IdeaPluginDescriptor,
  shouldNotifyUser: Boolean,
): PluginLoadingProblem = PluginLoadingProblem(
  plugin = plugin,
  shouldNotifyUser = shouldNotifyUser,
  detailedMessageSupplier = {
    CoreBundle.message("plugin.loading.error.long.ide.contains.conflicting.module", plugin.name, incompatiblePlugin.pluginId)
  },
  shortMessageSupplier = { CoreBundle.message("plugin.loading.error.short.ide.contains.conflicting.module", incompatiblePlugin.pluginId) },
)

internal fun pluginDependencyCannotBeLoaded(
  plugin: IdeaPluginDescriptor,
  dependency: DependencyRef,
  shouldNotifyUser: Boolean,
  dependencyDescriptor: IdeaPluginDescriptor? = null,
): PluginLoadingProblem {
  fun dependencyName(): @NlsSafe String = dependencyDescriptor?.name ?: dependency.idText()
  return PluginLoadingProblem(
    plugin = plugin,
    shouldNotifyUser = shouldNotifyUser,
    dependencyPluginId = dependencyDescriptor?.pluginId ?: when (dependency) {
      is DependencyRef.Plugin -> dependency.pluginId
      is DependencyRef.ContentModule -> null
    },
    dependency = dependency,
    detailedMessageSupplier = {
      CoreBundle.message("plugin.loading.error.long.depends.on.failed.to.load.plugin", plugin.name, dependencyName())
    },
    shortMessageSupplier = { CoreBundle.message("plugin.loading.error.short.depends.on.failed.to.load.plugin", dependencyName()) },
  )
}

internal fun pluginDependencyIsNotInstalled(
  plugin: IdeaPluginDescriptor,
  dependency: DependencyRef,
  shouldNotifyUser: Boolean,
  dependencyNameOrId: @NlsSafe String = dependency.idText(),
): PluginLoadingProblem = PluginLoadingProblem(
  plugin = plugin,
  shouldNotifyUser = shouldNotifyUser,
  dependency = dependency,
  detailedMessageSupplier = { CoreBundle.message("plugin.loading.error.long.depends.on.not.installed.plugin", plugin.name, dependencyNameOrId) },
  shortMessageSupplier = { CoreBundle.message("plugin.loading.error.short.depends.on.not.installed.plugin", dependencyNameOrId) },
)

internal fun pluginRequiresUnavailableModule(
  plugin: IdeaPluginDescriptor,
  moduleId: PluginModuleId,
  shouldNotifyUser: Boolean,
): PluginLoadingProblem = PluginLoadingProblem(
  plugin = plugin,
  shouldNotifyUser = shouldNotifyUser,
  dependency = DependencyRef.ContentModule(moduleId),
  detailedMessageSupplier = {
    CoreBundle.message("plugin.loading.error.long.depends.on.unavailable.module", plugin.name, moduleId.name)
  },
  shortMessageSupplier = { CoreBundle.message("plugin.loading.error.short.depends.on.unavailable.module", moduleId.name) },
)

internal fun pluginDependencyIsNotVisible(
  plugin: IdeaPluginDescriptor,
  dependencyModule: PluginModuleDescriptor,
  shouldNotifyUser: Boolean,
): PluginLoadingProblem {
  val dependency = when (dependencyModule) {
    is PluginMainDescriptor -> DependencyRef.Plugin(dependencyModule.pluginId)
    is ContentModuleDescriptor -> DependencyRef.ContentModule(dependencyModule.moduleId)
  }
  val owner = dependencyModule.getMainDescriptor()
  return PluginLoadingProblem(
    plugin = plugin,
    shouldNotifyUser = shouldNotifyUser,
    dependencyPluginId = owner.pluginId,
    dependency = dependency,
    detailedMessageSupplier = {
      CoreBundle.message("plugin.loading.error.long.depends.on.inaccessible.module", plugin.name, dependency.idText(), owner.name)
    },
    shortMessageSupplier = {
      CoreBundle.message("plugin.loading.error.short.depends.on.inaccessible.module", dependency.idText(), owner.name)
    },
  )
}

private fun DependencyRef.idText(): @NlsSafe String = when (this) {
  is DependencyRef.Plugin -> pluginId.idString
  is DependencyRef.ContentModule -> moduleId.name
}

internal fun pluginIsNotRequiredForLoadingTheExplicitlyConfiguredSubsetOfPlugins(
  plugin: IdeaPluginDescriptor,
): PluginLoadingProblem = PluginLoadingProblem(
  plugin = plugin,
  severity = PluginLoadingProblem.Severity.EXPECTED,
  shouldNotifyUser = false,
  detailedMessageSupplier = { CoreBundle.message("plugin.loading.error.long.not.required.for.explicitly.configured.subset", plugin.name) },
  shortMessageSupplier = { CoreBundle.message("plugin.loading.error.short.not.required.for.explicitly.configured.subset") },
)

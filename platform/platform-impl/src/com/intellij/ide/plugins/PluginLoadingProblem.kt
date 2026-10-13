// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.core.CoreBundle
import com.intellij.idea.AppMode
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.PlatformUtils
import com.intellij.util.system.CpuArch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

@ApiStatus.Experimental
class PluginLoadingProblem internal constructor(
  val plugin: IdeaPluginDescriptor,
  val shouldNotifyUser: Boolean,
  val disabledDependencyId: PluginId? = null,
  private val detailedMessageSupplier: () -> @NlsContexts.DetailedDescription String,
  private val shortMessageSupplier: () -> @NlsContexts.Label String,
) {
  val detailedMessage: @NlsContexts.DetailedDescription String get() = detailedMessageSupplier()
  val shortMessage: @NlsContexts.Label String get() = shortMessageSupplier()
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
): PluginLoadingProblem = PluginLoadingProblem(
  plugin = plugin,
  shouldNotifyUser = shouldNotifyUser,
  disabledDependencyId = dependencyId,
  detailedMessageSupplier = { CoreBundle.message("plugin.loading.error.long.depends.on.disabled.plugin", plugin.name, dependencyId) },
  shortMessageSupplier = { CoreBundle.message("plugin.loading.error.short.depends.on.disabled.plugin", dependencyId) },
)

internal fun nonBundledPluginsAreExplicitlyDisabled(plugin: IdeaPluginDescriptor): PluginLoadingProblem = PluginLoadingProblem(
  plugin = plugin,
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
  shouldNotifyUser = true,
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
  dependency: IdeaPluginDescriptor,
  shouldNotifyUser: Boolean,
): PluginLoadingProblem {
  fun dependencyName(): @NlsSafe String = dependency.name ?: dependency.pluginId.idString
  return PluginLoadingProblem(
    plugin = plugin,
    shouldNotifyUser = shouldNotifyUser,
    detailedMessageSupplier = {
      CoreBundle.message("plugin.loading.error.long.depends.on.failed.to.load.plugin", plugin.name, dependencyName())
    },
    shortMessageSupplier = { CoreBundle.message("plugin.loading.error.short.depends.on.failed.to.load.plugin", dependencyName()) },
  )
}

internal fun pluginDependencyIsNotInstalled(
  plugin: IdeaPluginDescriptor,
  dependencyNameOrId: @NlsSafe String,
  shouldNotifyUser: Boolean,
): PluginLoadingProblem = PluginLoadingProblem(
  plugin = plugin,
  shouldNotifyUser = shouldNotifyUser,
  detailedMessageSupplier = { CoreBundle.message("plugin.loading.error.long.depends.on.not.installed.plugin", plugin.name, dependencyNameOrId) },
  shortMessageSupplier = { CoreBundle.message("plugin.loading.error.short.depends.on.not.installed.plugin", dependencyNameOrId) },
)

internal fun pluginIsNotRequiredForLoadingTheExplicitlyConfiguredSubsetOfPlugins(
  plugin: IdeaPluginDescriptor,
): PluginLoadingProblem = PluginLoadingProblem(
  plugin = plugin,
  shouldNotifyUser = false,
  detailedMessageSupplier = { CoreBundle.message("plugin.loading.error.long.not.required.for.explicitly.configured.subset", plugin.name) },
  shortMessageSupplier = { CoreBundle.message("plugin.loading.error.short.not.required.for.explicitly.configured.subset") },
)

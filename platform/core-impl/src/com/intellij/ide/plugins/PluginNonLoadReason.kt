// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.core.CoreBundle
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

@ApiStatus.Experimental
sealed interface PluginNonLoadReason {
  val plugin: IdeaPluginDescriptor
  val detailedMessage: @NlsContexts.DetailedDescription String
  val shortMessage: @NlsContexts.Label String
  val logMessage: @NonNls String
  val shouldNotifyUser: Boolean
}

@ApiStatus.Internal
class PluginIsMarkedDisabled(
  override val plugin: IdeaPluginDescriptor,
) : PluginNonLoadReason {
  override val detailedMessage: @NlsContexts.DetailedDescription String
    get() = CoreBundle.message("plugin.loading.error.long.marked.disabled", plugin.name)
  override val shortMessage: @NlsContexts.Label String
    get() = CoreBundle.message("plugin.loading.error.short.marked.disabled")
  override val logMessage: @NonNls String
    get() = "Plugin '${plugin.name}' (${plugin.pluginId}) is marked disabled"
  override val shouldNotifyUser: Boolean = false
}

@ApiStatus.Internal
class PluginDependencyIsDisabled(
  override val plugin: IdeaPluginDescriptor,
  val dependencyId: PluginId, // TODO id is not enough, should show name instead; requires name resolution context
  override val shouldNotifyUser: Boolean,
) : PluginNonLoadReason {
  override val detailedMessage: @NlsContexts.DetailedDescription String
    get() = CoreBundle.message("plugin.loading.error.long.depends.on.disabled.plugin", plugin.name, dependencyId)
  override val shortMessage: @NlsContexts.Label String
    get() = CoreBundle.message("plugin.loading.error.short.depends.on.disabled.plugin", dependencyId)
  override val logMessage: @NonNls String
    get() = "Plugin '${plugin.name}' (${plugin.pluginId}) requires plugin with id=${dependencyId} to be enabled"
}

@ApiStatus.Internal
class PluginIsIncompatibleWithKotlinMode(
  override val plugin: IdeaPluginDescriptor,
  val mode: @Nls String
): PluginNonLoadReason {
  override val detailedMessage: @NlsContexts.DetailedDescription String
    get() = CoreBundle.message("plugin.loading.error.long.kotlin.incompatible", plugin.name, mode)
  override val shortMessage: @NlsContexts.Label String
    get() = CoreBundle.message("plugin.loading.error.short.kotlin.incompatible", mode)
  override val logMessage: @NonNls String
    get() = "Plugin '${plugin.name}' (${plugin.pluginId}) is incompatible with Kotlin in $mode mode"
  override val shouldNotifyUser: Boolean = false
}

@ApiStatus.Internal
class NonBundledPluginsAreExplicitlyDisabled(
  override val plugin: IdeaPluginDescriptor
): PluginNonLoadReason {
  override val detailedMessage: @NlsContexts.DetailedDescription String
    get() = CoreBundle.message("plugin.loading.error.long.custom.plugin.loading.disabled", plugin.name)
  override val shortMessage: @NlsContexts.Label String
    get() = CoreBundle.message("plugin.loading.error.short.custom.plugin.loading.disabled")
  override val logMessage: @NonNls String
    get() = "Plugin '${plugin.name}' (${plugin.pluginId}) is not loaded because non-bundled plugins are explicitly disabled"
  override val shouldNotifyUser: Boolean = false
}

@ApiStatus.Internal
class PluginIsMarkedBroken(
  override val plugin: IdeaPluginDescriptor,
): PluginNonLoadReason {
  // FIXME confusing messages
  override val detailedMessage: @NlsContexts.DetailedDescription String
    get() = CoreBundle.message("plugin.loading.error.long.marked.as.broken", plugin.name, plugin.version)
  override val shortMessage: @NlsContexts.Label String
    get() = CoreBundle.message("plugin.loading.error.short.marked.as.broken")
  override val logMessage: @NonNls String
    get() = "Plugin '${plugin.name}' (${plugin.pluginId}, version=${plugin.version}) is marked incompatible with the current version of the IDE"
  override val shouldNotifyUser: Boolean = true
}

@ApiStatus.Internal
class PluginIsCompatibleOnlyWithIntelliJIDEA(
  override val plugin: IdeaPluginDescriptor,
): PluginNonLoadReason {
  override val detailedMessage: @NlsContexts.DetailedDescription String
    get() = CoreBundle.message("plugin.loading.error.long.compatible.with.intellij.idea.only", plugin.name)
  override val shortMessage: @NlsContexts.Label String
    get() = CoreBundle.message("plugin.loading.error.short.compatible.with.intellij.idea.only")
  override val logMessage: @NonNls String
    get() = "Plugin '${plugin.name}' (${plugin.pluginId}) is compatible with IntelliJ IDEA only because it doesn''t define any explicit module dependencies"
  override val shouldNotifyUser: Boolean = true
}

@ApiStatus.Internal
class PluginIsIncompatibleWithHostPlatform(
  override val plugin: IdeaPluginDescriptor,
  val requiredOs: IdeaPluginOsRequirement,
  val hostOs: @NlsSafe String,
): PluginNonLoadReason {
  override val detailedMessage: @NlsContexts.DetailedDescription String
    get() = CoreBundle.message("plugin.loading.error.long.incompatible.with.platform", plugin.name, plugin.version, requiredOs, hostOs)
  override val shortMessage: @NlsContexts.Label String
    get() = CoreBundle.message("plugin.loading.error.short.incompatible.with.platform", requiredOs)
  override val logMessage: @NonNls String
    get() = "Plugin '${plugin.name}' (${plugin.pluginId}, version=${plugin.version}) requires platform ${requiredOs} but the current platform is ${hostOs}"
  override val shouldNotifyUser: Boolean = true
}

@ApiStatus.Internal
class PluginSinceBuildConstraintViolation(
  override val plugin: IdeaPluginDescriptor,
  val productBuildNumber: BuildNumber,
): PluginNonLoadReason {
  override val detailedMessage: @NlsContexts.DetailedDescription String
    get() = CoreBundle.message("plugin.loading.error.long.incompatible.since.build", plugin.name, plugin.version, plugin.sinceBuild, productBuildNumber)
  override val shortMessage: @NlsContexts.Label String
    get() = CoreBundle.message("plugin.loading.error.short.incompatible.since.build", plugin.sinceBuild)
  override val logMessage: @NonNls String
    get() = "Plugin '${plugin.name}' (${plugin.pluginId}, version=${plugin.version}) requires IDE build ${plugin.sinceBuild} or newer, but the current build is $productBuildNumber"
  override val shouldNotifyUser: Boolean = true
}

@ApiStatus.Internal
class PluginUntilBuildConstraintViolation(
  override val plugin: IdeaPluginDescriptor,
  val productBuildNumber: BuildNumber,
): PluginNonLoadReason {
  override val detailedMessage: @NlsContexts.DetailedDescription String
    get() = CoreBundle.message("plugin.loading.error.long.incompatible.until.build", plugin.name, plugin.version, plugin.untilBuild, productBuildNumber)
  override val shortMessage: @NlsContexts.Label String
    get() = CoreBundle.message("plugin.loading.error.short.incompatible.until.build", plugin.untilBuild)
  override val logMessage: @NonNls String
    get() = "Plugin '${plugin.name}' (${plugin.pluginId}, version=${plugin.version}) requires IDE build ${plugin.untilBuild} or older, but the current build is $productBuildNumber"
  override val shouldNotifyUser: Boolean = true
}

@ApiStatus.Internal
class PluginMalformedSinceUntilConstraints(
  override val plugin: IdeaPluginDescriptor,
): PluginNonLoadReason {
  override val detailedMessage: @NlsContexts.DetailedDescription String
    get() = CoreBundle.message("plugin.loading.error.long.failed.to.load.requirements.for.ide.version", plugin.name)
  override val shortMessage: @NlsContexts.Label String
    get() = CoreBundle.message("plugin.loading.error.short.failed.to.load.requirements.for.ide.version")
  override val logMessage: @NonNls String
    get() = "Plugin '${plugin.name}' (${plugin.pluginId}, version=${plugin.version}) has malformed constraints for IDE version"
  override val shouldNotifyUser: Boolean = true
}

@ApiStatus.Internal
class PluginLoadingIsDisabledCompletely(
  override val plugin: IdeaPluginDescriptor,
): PluginNonLoadReason {
  override val detailedMessage: @NlsContexts.DetailedDescription String
    get() = CoreBundle.message("plugin.loading.error.long.plugin.loading.disabled", plugin.name)
  override val shortMessage: @NlsContexts.Label String
    get() = CoreBundle.message("plugin.loading.error.short.plugin.loading.disabled")
  override val logMessage: @NonNls String
    get() = "Plugin '${plugin.name}' (${plugin.pluginId}) is not loaded because plugin loading is disabled completely"
  override val shouldNotifyUser: Boolean = true
}

@ApiStatus.Internal
class PluginPackagePrefixConflict(
  override val plugin: IdeaPluginDescriptorImpl,
  val module: IdeaPluginDescriptorImpl,
  val conflictingModule: IdeaPluginDescriptorImpl,
): PluginNonLoadReason {
  override val detailedMessage: @NlsContexts.DetailedDescription String
    get() = CoreBundle.message("plugin.loading.error.long.package.prefix.conflict", plugin.name, conflictingModule.name, module.moduleId, conflictingModule.moduleId)
  override val shortMessage: @NlsContexts.Label String
    get() = CoreBundle.message("plugin.loading.error.short.package.prefix.conflict", plugin.name, conflictingModule.name)
  override val logMessage: @NonNls String
    get() = "Plugin '${module.name}' conflicts with '${conflictingModule.name}' and may work incorrectly. " +
            "Their respective modules '${module.moduleId}' and '${conflictingModule.moduleId}' declare the same package prefix"
  override val shouldNotifyUser: Boolean = true

  private val IdeaPluginDescriptorImpl.moduleId: String get() = moduleName ?: pluginId.idString
}

@ApiStatus.Internal
class PluginIsIncompatibleWithAnotherPlugin(
  override val plugin: IdeaPluginDescriptor,
  val incompatiblePlugin: IdeaPluginDescriptor,
  override val shouldNotifyUser: Boolean,
): PluginNonLoadReason {
  // FIXME confusing message
  override val detailedMessage: @NlsContexts.DetailedDescription String
    get() = CoreBundle.message("plugin.loading.error.long.ide.contains.conflicting.module", plugin.name, incompatiblePlugin.pluginId)
  override val shortMessage: @NlsContexts.Label String
    get() = CoreBundle.message("plugin.loading.error.short.ide.contains.conflicting.module", incompatiblePlugin.pluginId)
  override val logMessage: @NonNls String
    get() = "Plugin '${plugin.name}' (${plugin.pluginId}) is incompatible with another plugin '${incompatiblePlugin.name}' (${incompatiblePlugin.pluginId})"
}

@ApiStatus.Internal
class PluginModuleDependencyCannotBeLoadedOrMissing(
  override val plugin: IdeaPluginDescriptor,
  val moduleDependency: ModuleDependencies.ModuleReference,
  override val shouldNotifyUser: Boolean,
): PluginNonLoadReason {
  // FIXME VERY confusing message
  override val detailedMessage: @NlsContexts.DetailedDescription String
    get() = CoreBundle.message("plugin.loading.error.long.depends.on.not.installed.plugin", plugin.name, moduleDependency.name)
  override val shortMessage: @NlsContexts.Label String
    get() = CoreBundle.message("plugin.loading.error.short.depends.on.not.installed.plugin", moduleDependency.name)
  override val logMessage: @NonNls String
    get() = "Plugin '${plugin.name}' (${plugin.pluginId}) has module dependency '${moduleDependency.name}' which cannot be loaded or missing"
}

@ApiStatus.Internal
class PluginDependencyCannotBeLoaded(
  override val plugin: IdeaPluginDescriptor,
  val dependencyNameOrId: @NlsSafe String,
  override val shouldNotifyUser: Boolean
): PluginNonLoadReason {
  override val detailedMessage: @NlsContexts.DetailedDescription String
    get() = CoreBundle.message("plugin.loading.error.long.depends.on.failed.to.load.plugin", plugin.name, dependencyNameOrId)
  override val shortMessage: @NlsContexts.Label String
    get() = CoreBundle.message("plugin.loading.error.short.depends.on.failed.to.load.plugin", dependencyNameOrId)
  override val logMessage: @NonNls String
    get() = "Plugin '${plugin.name}' (${plugin.pluginId}) has dependency on '${dependencyNameOrId}' which cannot be loaded"
}

@ApiStatus.Internal
class PluginDependencyIsNotInstalled(
  override val plugin: IdeaPluginDescriptor,
  val dependencyNameOrId: @NlsSafe String,
  override val shouldNotifyUser: Boolean
): PluginNonLoadReason {
  override val detailedMessage: @NlsContexts.DetailedDescription String
    get() = CoreBundle.message("plugin.loading.error.long.depends.on.not.installed.plugin", plugin.name, dependencyNameOrId)
  override val shortMessage: @NlsContexts.Label String
    get() = CoreBundle.message("plugin.loading.error.short.depends.on.not.installed.plugin", dependencyNameOrId)
  override val logMessage: @NonNls String
    get() = "Plugin '${plugin.name}' (${plugin.pluginId}) has dependency on '${dependencyNameOrId}' which is not installed"
}
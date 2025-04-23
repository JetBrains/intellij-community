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
import java.util.function.Supplier

@ApiStatus.Internal
sealed interface PluginNonLoadReason {
  val plugin: IdeaPluginDescriptor
  val detailedMessage: @NlsContexts.DetailedDescription String
  val shortMessage: @NlsContexts.Label String
  val logMessage: @NonNls String
  val shouldNotifyUser: Boolean
}

@ApiStatus.Internal
class PluginLoadingError internal constructor(
  override val plugin: IdeaPluginDescriptor,
  private val detailedMessageSupplier: Supplier<@NlsContexts.DetailedDescription String>,
  private val shortMessageSupplier: Supplier<@NlsContexts.Label String>,
  override val shouldNotifyUser: Boolean,
) : PluginNonLoadReason {
  internal constructor(
    plugin: IdeaPluginDescriptor,
    detailedMessageSupplier: Supplier<@NlsContexts.DetailedDescription String>,
    shortMessageSupplier: Supplier<@NlsContexts.Label String>,
  ) : this(
    plugin = plugin,
    detailedMessageSupplier = detailedMessageSupplier,
    shortMessageSupplier = shortMessageSupplier,
    shouldNotifyUser = true)

  @Suppress("HardCodedStringLiteral") // drop after KTIJ-32161
  override val detailedMessage: @NlsContexts.DetailedDescription String
    get() = detailedMessageSupplier.get()

  @Suppress("HardCodedStringLiteral") // drop after KTIJ-32161
  override val shortMessage: @NlsContexts.Label String
    get() = shortMessageSupplier.get()

  override val logMessage: @NonNls String
    get() = formatErrorMessage(plugin, detailedMessageSupplier.get())

  override fun toString(): @NonNls String = logMessage

  companion object {
    private fun formatErrorMessage(descriptor: IdeaPluginDescriptor, message: String): @NonNls String {
      val builder = StringBuilder()
      builder.append(descriptor.name).append(" (id=").append(descriptor.pluginId).append(", path=")
      builder.append(PluginUtils.pluginPathToUserString(descriptor.pluginPath))
      val version = descriptor.version
      if (version != null && !descriptor.isBundled && version != PluginManagerCore.buildNumber.asString()) {
        builder.append(", version=").append(version)
      }
      builder.append("): ").append(message)
      return builder.toString()
    }
  }
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
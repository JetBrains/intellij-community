// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.core.CoreBundle
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.NlsContexts
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
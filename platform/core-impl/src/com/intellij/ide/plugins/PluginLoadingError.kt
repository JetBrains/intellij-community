// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.util.function.Supplier

@ApiStatus.Internal
sealed interface PluginNonLoadReason {
  val plugin: IdeaPluginDescriptor
  val detailedMessage: @NlsContexts.DetailedDescription String
  val shortMessage: @NlsContexts.Label String
  val internalMessage: @NonNls String
  val isNotifyUser: Boolean
}

@ApiStatus.Internal
class PluginLoadingError internal constructor(
  override val plugin: IdeaPluginDescriptor,
  private val detailedMessageSupplier: Supplier<@NlsContexts.DetailedDescription String>,
  private val shortMessageSupplier: Supplier<@NlsContexts.Label String>,
  override val isNotifyUser: Boolean,
  @JvmField val disabledDependency: PluginId? = null,
) : PluginNonLoadReason {
  internal constructor(
    plugin: IdeaPluginDescriptor,
    detailedMessageSupplier: Supplier<@NlsContexts.DetailedDescription String>,
    shortMessageSupplier: Supplier<@NlsContexts.Label String>,
  ) : this(
    plugin = plugin,
    detailedMessageSupplier = detailedMessageSupplier,
    shortMessageSupplier = shortMessageSupplier,
    isNotifyUser = true)

  @Suppress("HardCodedStringLiteral") // drop after KTIJ-32161
  override val detailedMessage: @NlsContexts.DetailedDescription String
    get() = detailedMessageSupplier.get()

  @Suppress("HardCodedStringLiteral") // drop after KTIJ-32161
  override val shortMessage: @NlsContexts.Label String
    get() = shortMessageSupplier.get()

  override val internalMessage: @NonNls String
    get() = formatErrorMessage(plugin, detailedMessageSupplier.get())

  internal val isDisabledError: Boolean
    get() = shortMessageSupplier === DISABLED

  override fun toString(): @NonNls String = internalMessage

  companion object {
    internal val DISABLED: Supplier<String> = Supplier { "" }

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
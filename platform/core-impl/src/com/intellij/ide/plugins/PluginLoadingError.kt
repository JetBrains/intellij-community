// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.NonNls
import java.util.function.Supplier

class PluginLoadingError internal constructor(val plugin: IdeaPluginDescriptor,
                                              val detailedMessageSupplier: Supplier<@NlsContexts.DetailedDescription String>?,
                                              private val shortMessageSupplier: Supplier<@NlsContexts.Label String>,
                                              val isNotifyUser: Boolean,
                                              @JvmField val disabledDependency: PluginId? = null) {
  companion object {
    internal val DISABLED: Supplier<String> = Supplier { "" }

    private fun formatErrorMessage(descriptor: IdeaPluginDescriptor, message: String): @NonNls String {
      val builder = StringBuilder()
      builder.append("The ").append(descriptor.name).append(" (id=").append(descriptor.pluginId).append(", path=")
      builder.append(pluginPathToUserString(descriptor.pluginPath))
      val version = descriptor.version
      if (version != null && !descriptor.isBundled && version != PluginManagerCore.buildNumber.asString()) {
        builder.append(", version=").append(version)
      }
      builder.append(") plugin ").append(message)
      return builder.toString()
    }
  }

  internal constructor(plugin: IdeaPluginDescriptor,
                       detailedMessageSupplier: Supplier<@NlsContexts.DetailedDescription String>?,
                       shortMessageSupplier: Supplier<@NlsContexts.Label String>) :
    this(plugin = plugin,
      detailedMessageSupplier = detailedMessageSupplier,
      shortMessageSupplier = shortMessageSupplier,
      isNotifyUser = true)

  @get:NlsContexts.DetailedDescription
  val detailedMessage: String
    get() = detailedMessageSupplier!!.get()

  internal val isDisabledError: Boolean
    get() = shortMessageSupplier === DISABLED

  override fun toString(): @NonNls String = internalMessage

  val internalMessage: @NonNls String
    get() = formatErrorMessage(plugin, (detailedMessageSupplier ?: shortMessageSupplier).get())

  @get:NlsContexts.Label
  val shortMessage: String
    get() = shortMessageSupplier.get()
}
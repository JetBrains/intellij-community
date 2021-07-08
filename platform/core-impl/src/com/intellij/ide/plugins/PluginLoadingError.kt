// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.annotations.NonNls
import java.util.function.Supplier

class PluginLoadingError internal constructor(val plugin: IdeaPluginDescriptor,
                                              private val detailedMessageSupplier: Supplier<String>?,
                                              private val shortMessageSupplier: Supplier<String>,
                                              val isNotifyUser: Boolean,
                                              @JvmField val disabledDependency: PluginId? = null) {
  internal constructor(plugin: IdeaPluginDescriptor,
                       detailedMessageSupplier: Supplier<String>?,
                       shortMessageSupplier: Supplier<String>) : this(plugin = plugin,
                                                                      detailedMessageSupplier = detailedMessageSupplier,
                                                                      shortMessageSupplier = shortMessageSupplier,
                                                                      isNotifyUser = true)

  @get:NlsContexts.DetailedDescription
  val detailedMessage: String
    get() = detailedMessageSupplier!!.get()

  override fun toString() = internalMessage

  val internalMessage: @NonNls String
    get() = formatErrorMessage(plugin, (detailedMessageSupplier ?: shortMessageSupplier).get())

  @get:NlsContexts.Label
  val shortMessage: String
    get() = shortMessageSupplier.get()

  companion object {
    fun formatErrorMessage(descriptor: IdeaPluginDescriptor, message: String): @NonNls String {
      val path = descriptor.pluginPath.toString()
      val builder = StringBuilder()
      builder.append("The ").append(descriptor.name).append(" (id=").append(descriptor.pluginId).append(", path=")
      builder.append(FileUtil.getLocationRelativeToUserHome(path, false))
      val version = descriptor.version
      if (version != null && !descriptor.isBundled && version != PluginManagerCore.getBuildNumber().asString()) {
        builder.append(", version=").append(version)
      }
      builder.append(") plugin ").append(message)
      return builder.toString()
    }
  }
}
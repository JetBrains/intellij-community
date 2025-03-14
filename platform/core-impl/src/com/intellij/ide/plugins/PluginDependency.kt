// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PluginDependency internal constructor(override val pluginId: PluginId,
                                            val configFile: String?,
                                            override val isOptional: Boolean) : IdeaPluginDependency {
  @Transient
  private var subDescriptor: IdeaPluginDescriptorImpl? = null

  fun getSubDescriptor(): IdeaPluginDescriptorImpl? = subDescriptor

  fun setSubDescriptor(subDescriptor: IdeaPluginDescriptorImpl?) {
    this.subDescriptor = subDescriptor
  }

  override fun toString(): String {
    return "PluginDependency(" +
           "pluginId=" + pluginId +
           ", isOptional=" + isOptional +
           ", configFile=" + configFile +
           ", subDescriptor=" + subDescriptor +
           ')'
  }
}
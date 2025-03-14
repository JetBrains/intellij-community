// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PluginDependencyImpl internal constructor(override val pluginId: PluginId,
                                                override val configFile: String?,
                                                override val isOptional: Boolean) : PluginDependencyEx {
  @Transient
  private var subDescriptor: IdeaPluginDescriptorImpl? = null

  override fun getSubDescriptor(): IdeaPluginDescriptorImpl? = subDescriptor

  internal fun setSubDescriptor(subDescriptor: IdeaPluginDescriptorImpl?) {
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
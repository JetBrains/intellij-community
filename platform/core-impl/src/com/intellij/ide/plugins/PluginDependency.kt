// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PluginDependency internal constructor(override val pluginId: PluginId,
                                            val configFile: String?,
                                            override val isOptional: Boolean,
                                            @field:Transient var isDisabledOrBroken: Boolean) : IdeaPluginDependency {

  @Transient
  var subDescriptor: IdeaPluginDescriptorImpl? = null

  override fun toString(): String {
    return "PluginDependency(" +
           "pluginId=" + pluginId +
           ", isOptional=" + isOptional +
           ", configFile=" + configFile +
           ", isDisabledOrBroken=" + isDisabledOrBroken +
           ", subDescriptor=" + subDescriptor +
           ')'
  }
}
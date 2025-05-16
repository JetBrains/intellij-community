// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId

internal class PluginDependencyImpl internal constructor(
  override val pluginId: PluginId,
  override val configFile: String?,
  override val isOptional: Boolean,
) : PluginDependency {
  @Transient
  private var _subDescriptor: DependsSubDescriptor? = null

  override val subDescriptor: DependsSubDescriptor? get() = _subDescriptor

  internal fun setSubDescriptor(subDescriptor: DependsSubDescriptor?) {
    _subDescriptor = subDescriptor
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
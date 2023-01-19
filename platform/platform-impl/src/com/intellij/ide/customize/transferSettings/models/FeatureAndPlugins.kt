// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.models

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.Nls

abstract class FeatureInfo(@NlsSafe val name: String, @Nls val hint: String? = null, val isHidden: Boolean = false) {
  override fun equals(other: Any?) = other is FeatureInfo && name == other.name
  override fun hashCode() = 31 * (31 * name.hashCode() + (hint?.hashCode() ?: 0)) + isHidden.hashCode()
}

class BuiltInFeature(@NlsSafe name: String, @Nls hint: String? = null, isHidden: Boolean = false) : FeatureInfo(name, hint, isHidden)
class PluginFeature(val pluginId: String, @NlsSafe name: String, @Nls hint: String? = null, isHidden: Boolean = false) : FeatureInfo(name, hint, isHidden) {
  override fun equals(other: Any?) = other is PluginFeature && pluginId == other.pluginId
  override fun hashCode() = pluginId.hashCode()
}
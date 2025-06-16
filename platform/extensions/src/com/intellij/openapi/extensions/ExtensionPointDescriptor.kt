// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ExtensionPointDescriptor(@JvmField val name: String,
                               @JvmField val isNameQualified: Boolean,
                               @JvmField val className: String,
                               @JvmField val isBean: Boolean,
                               @JvmField val hasAttributes: Boolean,
                               @JvmField val isDynamic: Boolean) {
  fun getQualifiedName(pluginDescriptor: PluginDescriptor): String = if (isNameQualified) name else "${pluginDescriptor.pluginId}.$name"

  // getQualifiedName() can be used instead, but this method allows avoiding temp string creation
  fun nameEquals(qualifiedName: String, pluginDescriptor: PluginDescriptor): Boolean {
    if (isNameQualified) {
      return qualifiedName == name
    }
    else {
      val pluginId = pluginDescriptor.pluginId.idString
      return (qualifiedName.length == (pluginId.length + 1 + name.length)) &&
             qualifiedName[pluginId.length] == '.' &&
             qualifiedName.startsWith(pluginId) &&
             qualifiedName.endsWith(name)
    }
  }

  override fun toString(): String {
    return "ExtensionPointDescriptor(name=$name, isNameQualified=$isNameQualified, className=$className, isBean=$isBean, isDynamic=$isDynamic)"
  }
}
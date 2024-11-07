// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.util.xml.dom.XmlElement
import org.jetbrains.annotations.ApiStatus

class ExtensionDescriptor @ApiStatus.Internal constructor(
  @ApiStatus.Internal @JvmField val implementation: String?,
  @ApiStatus.Internal @JvmField val os: Os?,
  @ApiStatus.Internal @JvmField val orderId: String?,
  @ApiStatus.Internal @JvmField val order: LoadingOrder,
  @ApiStatus.Internal @JvmField val element: XmlElement?,
  @ApiStatus.Internal @JvmField val hasExtraAttributes: Boolean,
) {
  @Suppress("EnumEntryName")
  enum class Os {
    mac, linux, windows, unix, freebsd;

    @ApiStatus.Internal
    fun isSuitableForOs(): Boolean {
      return when (this) {
        mac -> SystemInfoRt.isMac
        linux -> SystemInfoRt.isLinux
        windows -> SystemInfoRt.isWindows
        unix -> SystemInfoRt.isUnix
        freebsd -> SystemInfoRt.isFreeBSD
        else -> throw IllegalArgumentException("Unknown OS '$this'")
      }
    }
  }
}

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
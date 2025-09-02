// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.util.xml.dom.XmlElement
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ExtensionDescriptor(
  @JvmField val implementation: String?,
  @JvmField val os: Os?,
  @JvmField val orderId: String?,
  @JvmField val order: LoadingOrder,
  @JvmField val element: XmlElement?,
  @JvmField val hasExtraAttributes: Boolean,
) {
  @ApiStatus.Internal
  @Suppress("EnumEntryName")
  enum class Os {
    mac, linux, windows, unix, freebsd;

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
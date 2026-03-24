// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions

import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
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
    windows, unix, mac, linux, freebsd;

    @OptIn(LowLevelLocalMachineAccess::class)
    fun isSuitableForOs(): Boolean = when (this) {
      windows -> OS.CURRENT == OS.Windows
      unix -> OS.CURRENT != OS.Windows
      mac -> OS.CURRENT == OS.macOS
      linux -> OS.CURRENT == OS.Linux
      freebsd -> OS.CURRENT == OS.FreeBSD
    }
  }
}

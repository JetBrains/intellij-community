// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.tcp.raw

import com.intellij.platform.eel.tcp.TcpEelConstants
import com.intellij.platform.eel.tcp.TcpEelDescriptor
import com.intellij.platform.eel.tcp.TcpEelPathParser
import com.intellij.platform.ijent.tcp.TcpEndpoint

class RawTcpPathParser : TcpEelPathParser {
  override fun isPathCompatible(path: String): Boolean {
    return path.startsWith(TcpEelConstants.TCP_RAW_PREFIX)

  }

  override fun extractInternalMachineId(path: String): String? {
    if (!isPathCompatible(path)) return null
    return path.substringAfter(TcpEelConstants.TCP_PROTOCOL_PREFIX).substringBefore("/")
  }

  override fun toDescriptor(internalName: String): TcpEelDescriptor? {
    if (!internalName.startsWith(TcpEelConstants.TCP_RAW_SCHEME)) return null
    val host = internalName.substringAfter(TcpEelConstants.TCP_RAW_SCHEME).drop(1)
    return RawTcpEelDescriptor(TcpEndpoint(host))
  }
}
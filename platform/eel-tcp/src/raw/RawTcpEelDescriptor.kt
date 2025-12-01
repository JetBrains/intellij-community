// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.tcp.raw

import com.intellij.platform.eel.tcp.TcpEelConstants
import com.intellij.platform.eel.tcp.TcpEelDescriptor
import com.intellij.platform.eel.tcp.toPath
import com.intellij.platform.ijent.tcp.TcpEndpoint
import org.jetbrains.annotations.NonNls

internal class RawTcpEelDescriptor(tcpEndpoint: TcpEndpoint) : TcpEelDescriptor() {
  override val rootPathString: String = "${TcpEelConstants.TCP_RAW_PREFIX}${tcpEndpoint.toPath()}"
  override val name: @NonNls String = "Raw Tcp Eel to ${tcpEndpoint.host}"
}
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.tcp.raw

import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.tcp.TcpEelDescriptor
import com.intellij.platform.ijent.tcp.TcpDeployInfo
import org.jetbrains.annotations.NonNls

internal class RawTcpEelDescriptor(val tcpDeploy: TcpDeployInfo.FixedPort, osFamily: EelOsFamily) : TcpEelDescriptor(osFamily) {

  override val rootPathString: String = "${RawTcpConsts.pathFromTcpDeploy(tcpDeploy, osFamily)}"
  override val name: @NonNls String = "Raw Tcp Eel to ${tcpDeploy.host}-${tcpDeploy.port}"
}
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.tcp.raw

import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.tcp.TcpEelConstants
import com.intellij.platform.ijent.tcp.TcpDeployInfo
import java.nio.file.Path
import kotlin.io.path.Path

object RawTcpConsts {
  const val SCHEME: String = "raw"
  fun extractTcpDeploy(internalName: String): TcpDeployInfo.FixedPort? {
    if (!internalName.startsWith("$SCHEME-")) return null
    val hostPort = internalName.removePrefix("$SCHEME-").split("-")
    if (hostPort.size != 2) return null
    val host = hostPort[0]
    val port = hostPort[1].toIntOrNull() ?: return null
    return TcpDeployInfo.FixedPort(host, port)
  }

  fun pathFromTcpDeploy(tcpDeploy: TcpDeployInfo.FixedPort, osFamily: EelOsFamily): Path =
    Path("${TcpEelConstants.TCP_PATH_PREFIX}$SCHEME-${tcpDeploy.host}-${tcpDeploy.port}-${osFamily.name.lowercase()}")

  fun internalName(tcpDeployInfo: TcpDeployInfo.FixedPort): String = "$SCHEME-${tcpDeployInfo.host}-${tcpDeployInfo.port}"
}
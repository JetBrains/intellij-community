// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("IjentProcessUtil")

package com.intellij.platform.ijent

import com.intellij.platform.ijent.tcp.TcpDeployInfo

/**
 * If [selfDeleteOnExit] is true, IJent tries to delete itself AND its parent directory during/after it has exited.
 */
fun getIjentGrpcArgv(
  remotePathToIjent: String,
  selfDeleteOnExit: Boolean = false,
  noShutdownOnDisconnect: Boolean = false,
  deployInfo: TcpDeployInfo? = null,
  useTLS: Boolean = false,
): List<String> {
  return listOfNotNull(
    remotePathToIjent,
    "grpc-server",
    if (deployInfo != null) "--address=${deployInfo.host}" else null,
    if (deployInfo != null && deployInfo is TcpDeployInfo.FixedPort) "--port=${deployInfo.port}" else null,
    if (useTLS) "--use-tls" else null,
    if (selfDeleteOnExit) "--self-delete-on-exit" else null,
    if (noShutdownOnDisconnect) "--no-shutdown-on-disconnect" else null,
  )
}
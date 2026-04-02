// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("IjentProcessUtil")

package com.intellij.platform.ijent

import com.intellij.openapi.util.registry.Registry
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
  val useMultiTransport = Registry.`is`("ijent.multiple.connections.mode")
  return listOfNotNull(
    remotePathToIjent,
    if (useMultiTransport) "grpc-multi-transport-server" else "grpc-server",
    if (!useMultiTransport && deployInfo != null) "--address=${deployInfo.host}" else null,
    if (!useMultiTransport && deployInfo != null && deployInfo is TcpDeployInfo.FixedPort) "--port=${deployInfo.port}" else null,
    if (!useMultiTransport && useTLS) "--use-tls" else null,
    if (selfDeleteOnExit) "--self-delete-on-exit" else null,
    if (!useMultiTransport && noShutdownOnDisconnect) "--no-shutdown-on-disconnect" else null,
  )
}
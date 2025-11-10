// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("IjentProcessUtil")

package com.intellij.platform.ijent

import com.intellij.openapi.diagnostic.Logger
import com.intellij.platform.ijent.tcp.TcpDeployInfo
import com.intellij.util.containers.map2Array

/**
 * If [selfDeleteOnExit] is true, IJent tries to delete itself AND its parent directory during/after it has exited.
 */
fun getIjentGrpcArgv(
  remotePathToIjent: String,
  additionalEnv: Map<String, String> = mapOf(),
  selfDeleteOnExit: Boolean = false,
  noShutdownOnDisconnect: Boolean = false,
  usrBinEnv: String = "/usr/bin/env",
  deployInfo: TcpDeployInfo? = null,
): List<String> {
  return listOfNotNull(
    usrBinEnv,
    *additionalEnv.entries.map2Array { (k, v) -> "$k=$v" },
    // "gdbserver", "0.0.0.0:12345",  // https://sourceware.org/gdb/onlinedocs/gdb/Connecting.html
    remotePathToIjent,
    "grpc-server",
    if (deployInfo != null) "--address=${deployInfo.host}" else null,
    if (deployInfo != null && deployInfo is TcpDeployInfo.FixedPort) "--port=${deployInfo.port}" else null,
    if (selfDeleteOnExit) "--self-delete-on-exit" else null,
    if (noShutdownOnDisconnect) "--no-shutdown-on-disconnect" else null,
  )
}

private val LOG = Logger.getInstance("com.intellij.platform.ijent.IjentProcessUtil")
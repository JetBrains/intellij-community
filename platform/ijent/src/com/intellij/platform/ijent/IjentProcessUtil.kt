// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("IjentProcessUtil")

package com.intellij.platform.ijent

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.containers.map2Array

/**
 * If [selfDeleteOnExit] is true, IJent tries to delete itself AND its parent directory during/after it has exited.
 */
fun getIjentGrpcArgv(
  remotePathToIjent: String,
  additionalEnv: Map<String, String> = mapOf(),
  selfDeleteOnExit: Boolean = false,
  usrBinEnv: String = "/usr/bin/env",
): List<String> {
  return listOfNotNull(
    usrBinEnv,
    *additionalEnv.entries.map2Array { (k, v) -> "$k=$v" },
    // "gdbserver", "0.0.0.0:12345",  // https://sourceware.org/gdb/onlinedocs/gdb/Connecting.html
    remotePathToIjent,
    "grpc-stdio-server",
    if (selfDeleteOnExit) "--self-delete-on-exit" else null,
  )
}

private val LOG = Logger.getInstance("com.intellij.platform.ijent.IjentProcessUtil")
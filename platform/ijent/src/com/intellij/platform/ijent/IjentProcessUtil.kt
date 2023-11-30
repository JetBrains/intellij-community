// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("IjentProcessUtil")

package com.intellij.platform.ijent

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.containers.map2Array

fun getIjentGrpcArgv(remotePathToIjent: String, additionalEnv: Map<String, String> = mapOf()): List<String> {
  val (debuggingLogLevel, backtrace) = when {
    ApplicationManager.getApplication()?.isUnitTestMode == true -> "trace" to true
    LOG.isTraceEnabled -> "trace" to true
    LOG.isDebugEnabled -> "debug" to true
    else -> "info" to false
  }

  return listOfNotNull(
    "/usr/bin/env",
    "RUST_LOG=ijent=$debuggingLogLevel",
    if (backtrace) "RUST_BACKTRACE=1" else null,
    *additionalEnv.entries.map2Array { (k, v) -> "$k=$v" },
    // "gdbserver", "0.0.0.0:12345",  // https://sourceware.org/gdb/onlinedocs/gdb/Connecting.html
    remotePathToIjent,
    "grpc-stdio-server",
  )
}

private val LOG = Logger.getInstance("com.intellij.platform.ijent.IjentProcessUtil")
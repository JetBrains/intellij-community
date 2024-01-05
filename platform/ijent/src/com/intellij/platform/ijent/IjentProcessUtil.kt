// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("IjentProcessUtil")

package com.intellij.platform.ijent

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.containers.map2Array

/**
 * If [selfDeleteOnExit] is true, IJent tries to delete itself AND its parent directory during/after it has exited.
 */
fun getIjentGrpcArgv(
  remotePathToIjent: String,
  additionalEnv: Map<String, String> = mapOf(),
  selfDeleteOnExit: Boolean = false,
): List<String> {
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
    if (selfDeleteOnExit) "--self-delete-on-exit" else null,
  )
}

fun ByteArray.toDebugString(offset: Int = 0, length: Int = size - offset): String = (offset until length).joinToString("") {
  var code = this[it].toInt()
  if (code < 0) {
    code += 0x100
  }
  if (code in 0x20..0x7e) {
    code.toChar().toString()
  }
  else {
    "\\x${code.toString(16).padStart(2, '0')}"
  }
}

private val LOG = Logger.getInstance("com.intellij.platform.ijent.IjentProcessUtil")
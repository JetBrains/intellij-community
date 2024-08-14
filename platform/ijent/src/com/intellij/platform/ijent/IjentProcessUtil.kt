// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("IjentProcessUtil")

package com.intellij.platform.ijent

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.registry.Registry
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
  val debuggingLogLevel = when {
    LOG.isTraceEnabled &&
    (ApplicationManager.getApplication()?.isUnitTestMode == true || System.getProperty("ijent.trace.grpc") == "true") ->
      "trace-with-grpc"

    LOG.isTraceEnabled -> "trace"
    LOG.isDebugEnabled -> "debug"
    else -> "info"
  }

  val communicationChannel = when (IjentCommunicationChannelType.getCurrentChannelType()) {
    IjentCommunicationChannelType.TCP_STDIO, IjentCommunicationChannelType.STDIO -> "stdio"
    IjentCommunicationChannelType.HYPER_V -> "vsock"
  }

  return listOfNotNull(
    usrBinEnv,
    *additionalEnv.entries.map2Array { (k, v) -> "$k=$v" },
    // "gdbserver", "0.0.0.0:12345",  // https://sourceware.org/gdb/onlinedocs/gdb/Connecting.html
    remotePathToIjent,
    "grpc-stdio-server",
    "--communication-channel", communicationChannel,
    "--log-level", debuggingLogLevel,
    if (selfDeleteOnExit) "--self-delete-on-exit" else null,
  )
}

enum class IjentCommunicationChannelType {
  TCP_STDIO,
  STDIO,
  HYPER_V;

  companion object {
    fun getCurrentChannelType(): IjentCommunicationChannelType {
      val registryValue = Registry.get("ijent.communication.channel.type")
      val selected = registryValue.selectedOption ?: registryValue.asString()
      return when (selected.lowercase()) {
        "tcp_stdio" -> TCP_STDIO
        "stdio" -> STDIO
        "hyperv" -> HYPER_V
        else -> {
          LOG.warn("Unexpected registry value for 'ijent.communication.channel.type': $selected. Using `TCP_STDIO` instead.")
          TCP_STDIO
        }
      }
    }
  }
}

private val LOG = Logger.getInstance("com.intellij.platform.ijent.IjentProcessUtil")
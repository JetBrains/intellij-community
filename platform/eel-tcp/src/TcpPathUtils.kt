// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.tcp

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.trace
import com.intellij.platform.ijent.tcp.TcpEndpoint

object TcpEelConstants {
  const val TCP_PREFIX: String = "/tcp"
  const val TCP_SCHEME: String = "tcp"
}

private val LOG = fileLogger()

internal fun String.extractTcpEndpoint(): TcpEndpoint? {
  // path format: /tcp-<host>/
  if (!startsWith(TcpEelConstants.TCP_PREFIX)) return null
  val host = drop(5).substringBefore("/")
  return TcpEndpoint(host).also { LOG.trace { "Extracting TCP endpoint from $this. Got $it" } }
}

internal fun TcpEndpoint.toPath(): String = host
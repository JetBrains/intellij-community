// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.tcp

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.trace
import com.intellij.platform.ijent.tcp.TcpEndpoint

object TcpEelConstants {
  const val TCP_PREFIX: String = "/tcp"

}

private val LOG = fileLogger()

internal fun String.extractTcpEndpoint(): TcpEndpoint? {
  // path format: tcp-host-port/
  // host could contain -
  if (!startsWith(TcpEelConstants.TCP_PREFIX)) return null
  //                           v /tcp-
  val hostAndPort = drop(5).substringBefore("/")
  val host = hostAndPort.substringBeforeLast('-')
  val port = hostAndPort.substringAfterLast('-').toIntOrNull() ?: return null
  return TcpEndpoint(host, port).also { LOG.trace { "Extracting TCP endpoint from $this. Got $it" } }
}

internal fun String.matchesTcpPrefix(): Boolean {
  return startsWith(TcpEelConstants.TCP_PREFIX)
}
internal fun TcpEndpoint.toPath(): String = "$host-${port}"
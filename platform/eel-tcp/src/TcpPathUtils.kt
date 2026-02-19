// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.tcp

import com.intellij.platform.ijent.tcp.TcpEndpoint
import java.io.File

object TcpEelConstants {

  @Suppress("IO_FILE_USAGE")
  private val IS_WINDOWS: Boolean = File.separatorChar == '\\'

  val TCP_PATH_PREFIX: String get() = if (IS_WINDOWS) "//tcp.ij/" else $$"/$tcp.ij/"

  const val TCP_PROTOCOL_PREFIX: String = "/tcp-"
  const val TCP_RAW_SCHEME: String = "raw"
  const val TCP_RAW_PREFIX: String = "/tcp-raw-"
}

internal fun TcpEndpoint.toPath(): String = host
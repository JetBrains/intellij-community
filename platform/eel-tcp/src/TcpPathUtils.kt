// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.tcp

import com.intellij.platform.ijent.tcp.TcpEndpoint

object TcpEelConstants {
  const val TCP_PREFIX: String = "/tcp"
  const val TCP_SCHEME: String = "tcp"
}

internal fun TcpEndpoint.toPath(): String = host
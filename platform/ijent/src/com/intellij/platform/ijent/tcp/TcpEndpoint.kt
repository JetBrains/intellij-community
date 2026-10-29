// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.tcp

import com.intellij.platform.util.annotations.VisibleToClasses

@VisibleToClasses(
  "com.intellij.platform.eel.tcp.TcpPathUtilsKt",
)
data class TcpEndpoint(val host: String)
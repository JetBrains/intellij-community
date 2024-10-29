// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.terminal

import com.intellij.openapi.util.SystemInfo

object JavaShellCommandUtils {
  fun getClassPathSeparator() = when {
    SystemInfo.isWindows -> ";"
    else -> ":"
  }
}
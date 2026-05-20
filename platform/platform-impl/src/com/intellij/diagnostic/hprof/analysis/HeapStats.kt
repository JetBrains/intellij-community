// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.hprof.analysis

import com.intellij.diagnostic.hprof.util.HeapReportUtils.toShortStringAsSize

internal class HeapStats {
  fun createReport() = buildString {
    with(Runtime.getRuntime()) {
      appendLine("Maximum heap size: ${toShortStringAsSize(maxMemory())}")
      appendLine("Committed heap size: ${toShortStringAsSize(totalMemory())}")
      appendLine("Free heap size: ${toShortStringAsSize(freeMemory())}")
    }
  }
}
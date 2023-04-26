// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.telemetry

import com.intellij.diagnostic.telemetry.MetricsExporterUtils.csvHeadersLines
import com.intellij.diagnostic.telemetry.MetricsExporterUtils.generateFileForMetrics
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.function.Supplier

class RollingFileSupplier(private val basePath: Path) : Supplier<Path> {
  private val maxSizeBeforeRoll: Long = 3 * 1024
  private var currentPath: Path? = null

  override fun get(): Path {
    val generateNewPath = currentPath?.let { Files.exists(it) && Files.size(it) > maxSizeBeforeRoll } ?: true
    if (generateNewPath) {
      currentPath = generateFileForMetrics(basePath)
      Files.write(currentPath, csvHeadersLines(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
    }
    return currentPath!!
  }
}
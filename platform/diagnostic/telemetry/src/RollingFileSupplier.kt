// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.telemetry

import com.intellij.diagnostic.telemetry.MetricsExporterUtils.csvHeadersLines
import com.intellij.diagnostic.telemetry.MetricsExporterUtils.generateFileForMetrics
import com.intellij.diagnostic.telemetry.MetricsExporterUtils.metricsReportingPath
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.function.Supplier

class RollingFileSupplier(private var currentPath: Path) : Supplier<Path> {
  private val basePath: Path? = metricsReportingPath()
  private val maxSizeBeforeRoll: Long = 30 * 1024 * 1024

  override fun get(): Path {
    if (Files.size(currentPath) > maxSizeBeforeRoll) {
      basePath?.let {
        currentPath = generateFileForMetrics(it)
        Files.write(currentPath, csvHeadersLines(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
      }
    }
    return currentPath
  }
}
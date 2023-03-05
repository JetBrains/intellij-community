// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.telemetry

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileSetLimiter
import com.intellij.util.SystemProperties
import java.nio.file.Path

class MetricsFileManager {
  companion object {
    fun generateFileForMetrics(metricsReportingPath: String): Path {
      synchronized(this) {
        val suffixDateFormat = System.getProperty("idea.diagnostic.opentelemetry.metrics.suffix-date-format", "yyyy-MM-dd-HH-mm-ss")
        val maxFilesToKeep = SystemProperties.getIntProperty("idea.diagnostic.opentelemetry.metrics.max-files-to-keep", 14)

        //if metrics path is relative -> resolve it against IDEA logDir:
        val pathResolvedAgainstLogDir = PathManager.getLogDir().resolve(metricsReportingPath).toAbsolutePath()

        return FileSetLimiter.inDirectory(pathResolvedAgainstLogDir.parent)
          .withBaseNameAndDateFormatSuffix(pathResolvedAgainstLogDir.fileName.toString(), suffixDateFormat)
          .removeOldFilesBut(maxFilesToKeep, FileSetLimiter.DELETE_ASYNC)
          .createNewFile()
      }
    }
  }
}
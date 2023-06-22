// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.intellij.openapi.application.PathManager
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.createDirectories
import kotlin.io.path.div

object IndexDiagnosticDumperUtils {
  val jacksonMapper: ObjectMapper by lazy {
    jacksonObjectMapper().registerKotlinModule()
  }

  val diagnosticTimestampFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss.SSS")

  fun writeValue(file: Path, value: Any) {
    file.parent.createDirectories()
    jacksonMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), value)
  }

  fun getDumpFilePath(prefix: String, time: LocalDateTime, extension: String, parent: Path, suffix: String = "" ): Path {
    val timestamp = time.format(diagnosticTimestampFormat)
    return parent / "$prefix$suffix$timestamp.$extension"
  }

  val indexingDiagnosticDir: Path by lazy {
    val logPath = PathManager.getLogPath()
    Paths.get(logPath).resolve("indexing-diagnostic")
  }

  val oldVersionIndexingDiagnosticDir: Path by lazy {
    val logPath = PathManager.getLogPath()
    Paths.get(logPath).resolve("old-version-indexing-diagnostic")
  }
}

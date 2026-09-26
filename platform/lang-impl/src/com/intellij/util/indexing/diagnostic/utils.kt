// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.Key
import com.intellij.testFramework.TestModeFlags
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.createDirectories
import kotlin.io.path.div

object IndexDiagnosticDumperUtils {
  val testDiagnosticPathFlag: Key<Path> = Key("IndexDiagnosticDumperUtils.testDiagnosticPathFlag")

  val jacksonMapper: ObjectMapper by lazy {
    jacksonObjectMapper()
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

  private val productionIndexingDiagnosticDir: Path by lazy {
    val logPath = PathManager.getLogPath()
    Paths.get(logPath).resolve("indexing-diagnostic")
  }

  val indexingDiagnosticDir: Path
    get() {
      if (ApplicationManager.getApplication().isUnitTestMode) {
        TestModeFlags.get(testDiagnosticPathFlag)?.let { return it.resolve("indexing-diagnostic") }
      }
      return productionIndexingDiagnosticDir
    }

  val oldVersionIndexingDiagnosticDir: Path by lazy {
    val logPath = PathManager.getLogPath()
    Paths.get(logPath).resolve("old-version-indexing-diagnostic")
  }
}

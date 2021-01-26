// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.getProjectCachePath
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.NonUrgentExecutor
import com.intellij.util.indexing.diagnostic.dto.JsonIndexDiagnostic
import com.intellij.util.indexing.diagnostic.presentation.generateHtml
import com.intellij.util.io.createDirectories
import com.intellij.util.io.delete
import com.intellij.util.io.exists
import com.intellij.util.io.write
import org.jetbrains.annotations.TestOnly
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.streams.asSequence

object IndexDiagnosticDumper {

  @JvmStatic
  private val shouldDumpDiagnosticsForInterruptedUpdaters: Boolean get() =
    SystemProperties.getBooleanProperty("intellij.indexes.diagnostics.should.dump.for.interrupted.index.updaters", false)

  @JvmStatic
  private val indexingDiagnosticsLimitOfFiles: Int get() =
    SystemProperties.getIntProperty("intellij.indexes.diagnostics.limit.of.files", 20)

  @JvmStatic
  val shouldDumpPathsOfIndexedFiles: Boolean get() =
    SystemProperties.getBooleanProperty("intellij.indexes.diagnostics.should.dump.paths.of.indexed.files", false)

  @JvmStatic
  @TestOnly
  var shouldDumpInUnitTestMode: Boolean = false

  val indexingDiagnosticDir: Path by lazy {
    val logPath = PathManager.getLogPath()
    Paths.get(logPath).resolve("indexing-diagnostic")
  }

  private val LOG = Logger.getInstance(IndexDiagnosticDumper::class.java)

  private val jacksonMapper: ObjectMapper by lazy {
    jacksonObjectMapper().registerKotlinModule()
  }

  private val diagnosticDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss.SSS")

  private const val fileNamePrefix = "diagnostic-"

  private var lastTime: LocalDateTime = LocalDateTime.MIN

  interface ProjectIndexingHistoryListener {
    companion object {
      val EP_NAME = ExtensionPointName.create<ProjectIndexingHistoryListener>("com.intellij.projectIndexingHistoryListener")
    }

    fun onFinishedIndexing(projectIndexingHistory: ProjectIndexingHistory)
  }

  fun dumpProjectIndexingHistoryIfNecessary(projectIndexingHistory: ProjectIndexingHistory) {
    try {
      if (ApplicationManager.getApplication().isUnitTestMode && !shouldDumpInUnitTestMode) {
        return
      }
      if (projectIndexingHistory.times.wasInterrupted && !shouldDumpDiagnosticsForInterruptedUpdaters) {
        return
      }
      NonUrgentExecutor.getInstance().execute { dumpProjectIndexingHistoryToLogSubdirectory(projectIndexingHistory) }
    } finally {
      ProjectIndexingHistoryListener.EP_NAME.forEachExtensionSafe { it.onFinishedIndexing(projectIndexingHistory) }
    }
  }

  fun readJsonIndexDiagnostic(file: Path): JsonIndexDiagnostic =
    jacksonMapper.readValue(file.toFile(), JsonIndexDiagnostic::class.java)

  @Synchronized
  private fun dumpProjectIndexingHistoryToLogSubdirectory(projectIndexingHistory: ProjectIndexingHistory) {
    try {
      val indexDiagnosticDirectory = projectIndexingHistory.project.getProjectCachePath(indexingDiagnosticDir)
      indexDiagnosticDirectory.createDirectories()

      var nowTime = LocalDateTime.now()
      if (lastTime == nowTime) {
        // Ensure that the generated diagnostic file does not overwrite an existing file.
        nowTime = nowTime.plusNanos(TimeUnit.MILLISECONDS.toNanos(1))
        lastTime = nowTime
      }

      val timestamp = nowTime.format(diagnosticDateTimeFormatter)
      val diagnosticJson = indexDiagnosticDirectory.resolve("$fileNamePrefix$timestamp.json")
      val diagnosticHtml = indexDiagnosticDirectory.resolve("$fileNamePrefix$timestamp.html")

      val jsonIndexDiagnostic = JsonIndexDiagnostic.generateForHistory(projectIndexingHistory)
      jacksonMapper.writerWithDefaultPrettyPrinter().writeValue(diagnosticJson.toFile(), jsonIndexDiagnostic)
      diagnosticHtml.write(jsonIndexDiagnostic.generateHtml())

      data class ExistingDiagnostic(val timestamp: LocalDateTime, val jsonFile: Path, val htmlFile: Path)

      val existingDiagnostics = Files.list(indexDiagnosticDirectory).use { files ->
        files.asSequence()
          .filter { file -> file.fileName.toString().startsWith(fileNamePrefix) && file.extension == "json" }
          .mapNotNull { jsonFile ->
            val timeStampString = jsonFile.fileName.toString().substringAfter(fileNamePrefix).substringBefore(".json")
            val timeStamp = try {
              LocalDateTime.parse(timeStampString, diagnosticDateTimeFormatter)
            }
            catch (e: Exception) {
              return@mapNotNull null
            }
            val htmlFile = jsonFile.resolveSibling(jsonFile.nameWithoutExtension + ".html")
            if (!htmlFile.exists()) {
              return@mapNotNull null
            }
            ExistingDiagnostic(timeStamp, jsonFile, htmlFile)
          }
          .toList()
      }

      val survivedDiagnostics = existingDiagnostics
        .sortedByDescending { it.timestamp }
        .take(indexingDiagnosticsLimitOfFiles)

      Files
        .list(indexDiagnosticDirectory)
        .use { files ->
          files
            .asSequence()
            .filter { it.extension == "json" || it.extension == "html" }
            .filter { file -> survivedDiagnostics.none { diagnostic -> file == diagnostic.htmlFile || file == diagnostic.jsonFile } }
            .forEach { it.delete() }
        }
    }
    catch (e: Exception) {
      LOG.warn("Failed to dump index diagnostic", e)
    }
  }

}
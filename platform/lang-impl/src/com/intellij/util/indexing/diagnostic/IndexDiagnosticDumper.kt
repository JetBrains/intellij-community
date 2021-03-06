// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.getProjectCachePath
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.NonUrgentExecutor
import com.intellij.util.indexing.diagnostic.dto.JsonIndexDiagnostic
import com.intellij.util.indexing.diagnostic.dto.JsonProjectIndexingHistoryTimes
import com.intellij.util.indexing.diagnostic.presentation.createAggregateHtml
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
import kotlin.io.path.*
import kotlin.streams.asSequence

class IndexDiagnosticDumper : Disposable {

  companion object {
    @JvmStatic
    fun getInstance(): IndexDiagnosticDumper = service()

    private val diagnosticDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss.SSS")

    private const val fileNamePrefix = "diagnostic-"

    @JvmStatic
    private val shouldDumpDiagnosticsForInterruptedUpdaters: Boolean
      get() =
        SystemProperties.getBooleanProperty("intellij.indexes.diagnostics.should.dump.for.interrupted.index.updaters", false)

    @JvmStatic
    private val indexingDiagnosticsLimitOfFiles: Int
      get() =
        SystemProperties.getIntProperty("intellij.indexes.diagnostics.limit.of.files", 20)

    @JvmStatic
    val shouldDumpPathsOfIndexedFiles: Boolean
      get() =
        SystemProperties.getBooleanProperty("intellij.indexes.diagnostics.should.dump.paths.of.indexed.files", false)

    @JvmStatic
    @TestOnly
    var shouldDumpInUnitTestMode: Boolean = false

    private val LOG = Logger.getInstance(IndexDiagnosticDumper::class.java)

    private val jacksonMapper: ObjectMapper by lazy {
      jacksonObjectMapper().registerKotlinModule()
    }

    fun readJsonIndexDiagnostic(file: Path): JsonIndexDiagnostic =
      jacksonMapper.readValue(file.toFile(), JsonIndexDiagnostic::class.java)

    val indexingDiagnosticDir: Path by lazy {
      val logPath = PathManager.getLogPath()
      Paths.get(logPath).resolve("indexing-diagnostic")
    }
  }

  private var isDisposed = false

  interface ProjectIndexingHistoryListener {
    companion object {
      val EP_NAME = ExtensionPointName.create<ProjectIndexingHistoryListener>("com.intellij.projectIndexingHistoryListener")
    }

    fun onStartedIndexing(projectIndexingHistory: ProjectIndexingHistory) = Unit

    fun onFinishedIndexing(projectIndexingHistory: ProjectIndexingHistory)
  }

  fun onIndexingStarted(projectIndexingHistory: ProjectIndexingHistory) {
    runAllListenersSafely { onStartedIndexing(projectIndexingHistory) }
  }

  fun onIndexingFinished(projectIndexingHistory: ProjectIndexingHistory) {
    try {
      if (ApplicationManager.getApplication().isUnitTestMode && !shouldDumpInUnitTestMode) {
        return
      }
      if (projectIndexingHistory.times.wasInterrupted && !shouldDumpDiagnosticsForInterruptedUpdaters) {
        return
      }
      NonUrgentExecutor.getInstance().execute { dumpProjectIndexingHistoryToLogSubdirectory(projectIndexingHistory) }
    }
    finally {
      runAllListenersSafely { onFinishedIndexing(projectIndexingHistory) }
    }
  }

  private fun runAllListenersSafely(block: ProjectIndexingHistoryListener.() -> Unit) {
    val listeners = ProgressManager.getInstance().computeInNonCancelableSection<List<ProjectIndexingHistoryListener>, Exception> {
      ProjectIndexingHistoryListener.EP_NAME.extensionList
    }
    for (listener in listeners) {
      try {
        listener.block()
      }
      catch (e: Exception) {
        if (e is ControlFlowException) {
          // Make all listeners run first.
          continue
        }
        LOG.error(e)
      }
    }
  }

  @Synchronized
  private fun dumpProjectIndexingHistoryToLogSubdirectory(projectIndexingHistory: ProjectIndexingHistory) {
    try {
      check(!isDisposed)

      val indexDiagnosticDirectory = projectIndexingHistory.project.getProjectCachePath(indexingDiagnosticDir)
      indexDiagnosticDirectory.createDirectories()

      val (diagnosticJson: Path, diagnosticHtml: Path) = getFilesForNewJsonAndHtmlDiagnostics(indexDiagnosticDirectory)

      val jsonIndexDiagnostic = JsonIndexDiagnostic.generateForHistory(projectIndexingHistory)
      jacksonMapper.writerWithDefaultPrettyPrinter().writeValue(diagnosticJson.toFile(), jsonIndexDiagnostic)
      diagnosticHtml.write(jsonIndexDiagnostic.generateHtml())

      val existingDiagnostics = parseExistingDiagnostics(indexDiagnosticDirectory)
      val survivedDiagnostics = deleteOutdatedDiagnostics(existingDiagnostics)
      indexDiagnosticDirectory.resolve("report.html").writeText(
        createAggregateHtml(projectIndexingHistory.project.name, survivedDiagnostics)
      )
    }
    catch (e: Exception) {
      LOG.warn("Failed to dump index diagnostic", e)
    }
  }

  private fun getFilesForNewJsonAndHtmlDiagnostics(indexDiagnosticDirectory: Path): Pair<Path, Path> {
    var diagnosticJson: Path
    var diagnosticHtml: Path
    var nowTime = LocalDateTime.now()
    while (true) {
      val timestamp = nowTime.format(diagnosticDateTimeFormatter)
      diagnosticJson = indexDiagnosticDirectory.resolve("$fileNamePrefix$timestamp.json")
      diagnosticHtml = indexDiagnosticDirectory.resolve("$fileNamePrefix$timestamp.html")
      if (!diagnosticJson.exists() && !diagnosticHtml.exists()) {
        break
      }
      nowTime = nowTime.plusNanos(TimeUnit.MILLISECONDS.toNanos(1))
    }
    return diagnosticJson to diagnosticHtml
  }

  private fun fastReadIndexingHistoryTimes(jsonFile: Path): JsonProjectIndexingHistoryTimes? {
    try {
      jsonFile.bufferedReader().use { reader ->
        jacksonMapper.factory.createParser(reader).use { parser ->
          while (parser.nextToken() != null) {
            val property = parser.currentName
            if (property == "times") {
              parser.nextToken()
              return jacksonMapper.readValue(parser)
            }
          }
        }
      }
    }
    catch (e: Exception) {
      LOG.debug("Failed to parse project indexing time", e)
    }
    return null
  }

  private fun deleteOutdatedDiagnostics(existingDiagnostics: List<ExistingDiagnostic>): List<ExistingDiagnostic> {
    val sortedDiagnostics = existingDiagnostics.sortedByDescending { it.indexingTimes.updatingStart.instant }

    val survivedDiagnostics = sortedDiagnostics.take(indexingDiagnosticsLimitOfFiles)
    val outdatedDiagnostics = sortedDiagnostics.drop(indexingDiagnosticsLimitOfFiles)

    for (diagnostic in outdatedDiagnostics) {
      diagnostic.jsonFile.delete()
      diagnostic.htmlFile.delete()
    }
    return survivedDiagnostics
  }

  private fun parseExistingDiagnostics(indexDiagnosticDirectory: Path): List<ExistingDiagnostic> =
    Files.list(indexDiagnosticDirectory).use { files ->
      files.asSequence()
        .filter { file -> file.fileName.toString().startsWith(fileNamePrefix) && file.extension == "json" }
        .mapNotNull { jsonFile ->
          val times = fastReadIndexingHistoryTimes(jsonFile) ?: return@mapNotNull null
          val htmlFile = jsonFile.resolveSibling(jsonFile.nameWithoutExtension + ".html")
          if (!htmlFile.exists()) {
            return@mapNotNull null
          }
          ExistingDiagnostic(jsonFile, htmlFile, times)
        }
        .toList()
    }

  data class ExistingDiagnostic(
    val jsonFile: Path,
    val htmlFile: Path,
    val indexingTimes: JsonProjectIndexingHistoryTimes
  )

  @Synchronized
  override fun dispose() {
    // The synchronized block allows to wait for unfinished background dumpers.
    isDisposed = true
  }

}
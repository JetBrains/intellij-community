// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectCachePath
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.NonUrgentExecutor
import com.intellij.util.indexing.diagnostic.dto.*
import com.intellij.util.indexing.diagnostic.presentation.createAggregateHtml
import com.intellij.util.indexing.diagnostic.presentation.generateHtml
import com.intellij.util.io.*
import org.jetbrains.annotations.TestOnly
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.io.path.bufferedReader
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.streams.asSequence

class IndexDiagnosticDumper : Disposable {
  companion object {
    @JvmStatic
    fun getInstance(): IndexDiagnosticDumper = service()

    val diagnosticTimestampFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss.SSS")

    private const val fileNamePrefix = "diagnostic-"

    @JvmStatic
    val projectIndexingHistoryListenerEpName = ExtensionPointName.create<ProjectIndexingHistoryListener>("com.intellij.projectIndexingHistoryListener")

    @JvmStatic
    private val shouldDumpDiagnosticsForInterruptedUpdaters: Boolean
      get() =
        SystemProperties.getBooleanProperty("intellij.indexes.diagnostics.should.dump.for.interrupted.index.updaters", false)

    @JvmStatic
    private val indexingDiagnosticsLimitOfFiles: Int
      get() =
        SystemProperties.getIntProperty("intellij.indexes.diagnostics.limit.of.files", 300)

    @JvmStatic
    val shouldDumpPathsOfIndexedFiles: Boolean
      get() =
        SystemProperties.getBooleanProperty("intellij.indexes.diagnostics.should.dump.paths.of.indexed.files", false)

    @JvmStatic
    val shouldDumpProviderRootPaths: Boolean
      get() =
        SystemProperties.getBooleanProperty("intellij.indexes.diagnostics.should.dump.provider.root.paths", false)

    /**
     * Some processes may be done in multiple threads, like content loading,
     * see [com.intellij.util.indexing.contentQueue.IndexUpdateRunner.doIndexFiles]
     * Such processes have InAllThreads time and visible time, see [com.intellij.util.indexing.contentQueue.IndexUpdateRunner.indexFiles],
     * [ProjectIndexingHistoryImpl.visibleTimeToAllThreadsTimeRatio], [IndexingFileSetStatistics]
     *
     * This property allows to provide more details on those times and ratio in html
     */
    @JvmStatic
    val shouldProvideVisibleAndAllThreadsTimeInfo: Boolean
      get() =
        SystemProperties.getBooleanProperty("intellij.indexes.diagnostics.should.provide.visible.and.all.threads.time.info", false)

    @JvmStatic
    @TestOnly
    var shouldDumpInUnitTestMode: Boolean = false

    private val LOG = Logger.getInstance(IndexDiagnosticDumper::class.java)

    val jacksonMapper: ObjectMapper by lazy {
      jacksonObjectMapper().registerKotlinModule()
    }

    fun readJsonIndexDiagnostic(file: Path): JsonIndexDiagnostic =
      jacksonMapper.readValue(file.toFile(), JsonIndexDiagnostic::class.java)

    fun clearDiagnostic() {
      if (indexingDiagnosticDir.exists()) {
        indexingDiagnosticDir.directoryStreamIfExists { dirStream ->
          dirStream.forEach { FileUtil.deleteWithRenaming(it) }
        }
      }
    }

    val indexingDiagnosticDir: Path by lazy {
      val logPath = PathManager.getLogPath()
      Paths.get(logPath).resolve("indexing-diagnostic")
    }

    fun getProjectDiagnosticDirectory(project: Project): Path {
      val directory = project.getProjectCachePath(indexingDiagnosticDir)
      directory.createDirectories()
      return directory
    }
  }

  private var isDisposed = false

  fun onIndexingStarted(projectIndexingHistory: ProjectIndexingHistoryImpl) {
    runAllListenersSafely { onStartedIndexing(projectIndexingHistory) }
  }

  fun onIndexingFinished(projectIndexingHistory: ProjectIndexingHistoryImpl) {
    try {
      if (ApplicationManager.getApplication().isUnitTestMode && !shouldDumpInUnitTestMode) {
        return
      }
      if (projectIndexingHistory.times.wasInterrupted && !shouldDumpDiagnosticsForInterruptedUpdaters) {
        return
      }
      projectIndexingHistory.indexingFinished()
      NonUrgentExecutor.getInstance().execute { dumpProjectIndexingHistoryToLogSubdirectory(projectIndexingHistory) }
    }
    finally {
      runAllListenersSafely { onFinishedIndexing(projectIndexingHistory) }
    }
  }

  private fun runAllListenersSafely(block: ProjectIndexingHistoryListener.() -> Unit) {
    val listeners = ProgressManager.getInstance().computeInNonCancelableSection<List<ProjectIndexingHistoryListener>, Exception> {
      projectIndexingHistoryListenerEpName.extensionList
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
  private fun dumpProjectIndexingHistoryToLogSubdirectory(projectIndexingHistory: ProjectIndexingHistoryImpl) {
    try {
      check(!isDisposed)

      val indexDiagnosticDirectory = getProjectDiagnosticDirectory(projectIndexingHistory.project)

      val (diagnosticJson: Path, diagnosticHtml: Path) = getFilesForNewJsonAndHtmlDiagnostics(indexDiagnosticDirectory)

      val jsonIndexDiagnostic = JsonIndexDiagnostic.generateForHistory(projectIndexingHistory)
      jacksonMapper.writerWithDefaultPrettyPrinter().writeValue(diagnosticJson.toFile(), jsonIndexDiagnostic)
      diagnosticHtml.write(jsonIndexDiagnostic.generateHtml())

      val existingDiagnostics = parseExistingDiagnostics(indexDiagnosticDirectory)
      val survivedDiagnostics = deleteOutdatedDiagnostics(existingDiagnostics)
      val sharedIndexEvents = SharedIndexDiagnostic.readEvents(projectIndexingHistory.project)
      val changedFilesPushedEvents = ChangedFilesPushedDiagnostic.readEvents(projectIndexingHistory.project)
      indexDiagnosticDirectory.resolve("report.html").write(
        createAggregateHtml(projectIndexingHistory.project.name, survivedDiagnostics, sharedIndexEvents, changedFilesPushedEvents)
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
      val timestamp = nowTime.format(diagnosticTimestampFormat)
      diagnosticJson = indexDiagnosticDirectory.resolve("$fileNamePrefix$timestamp.json")
      diagnosticHtml = indexDiagnosticDirectory.resolve("$fileNamePrefix$timestamp.html")
      if (!diagnosticJson.exists() && !diagnosticHtml.exists()) {
        break
      }
      nowTime = nowTime.plusNanos(TimeUnit.MILLISECONDS.toNanos(1))
    }
    return diagnosticJson to diagnosticHtml
  }

  private fun <T> fastReadJsonField(jsonFile: Path, propertyName: String, type: Class<T>): T? {
    try {
      jsonFile.bufferedReader().use { reader ->
        jacksonMapper.factory.createParser(reader).use { parser ->
          while (parser.nextToken() != null) {
            val property = parser.currentName
            if (property == propertyName) {
              parser.nextToken()
              return jacksonMapper.readValue(parser, type)
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

  private fun fastReadIndexingHistoryTimes(jsonFile: Path): JsonProjectIndexingHistoryTimes? =
    fastReadJsonField(jsonFile, "times", JsonProjectIndexingHistoryTimes::class.java)

  private fun fastReadFileCount(jsonFile: Path): JsonProjectIndexingFileCount? =
    fastReadJsonField(jsonFile, "fileCount", JsonProjectIndexingFileCount::class.java)

  private fun fastReadAppInfo(jsonFile: Path): JsonIndexDiagnosticAppInfo? =
    fastReadJsonField(jsonFile, "appInfo", JsonIndexDiagnosticAppInfo::class.java)

  private fun fastReadRuntimeInfo(jsonFile: Path): JsonRuntimeInfo? =
    fastReadJsonField(jsonFile, "runtimeInfo", JsonRuntimeInfo::class.java)

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
          val appInfo = fastReadAppInfo(jsonFile) ?: return@mapNotNull null
          val runtimeInfo = fastReadRuntimeInfo(jsonFile) ?: return@mapNotNull null
          val fileCount = fastReadFileCount(jsonFile)

          val htmlFile = jsonFile.resolveSibling(jsonFile.nameWithoutExtension + ".html")
          if (!htmlFile.exists()) {
            return@mapNotNull null
          }
          ExistingDiagnostic(jsonFile, htmlFile, times, appInfo, runtimeInfo, fileCount)
        }
        .toList()
    }

  data class ExistingDiagnostic(
    val jsonFile: Path,
    val htmlFile: Path,
    val indexingTimes: JsonProjectIndexingHistoryTimes,
    val appInfo: JsonIndexDiagnosticAppInfo,
    val runtimeInfo: JsonRuntimeInfo,
    // May be not available in existing local reports. After some time
    // (when all local reports are likely to expire) this field can be made non-null.
    val fileCount: JsonProjectIndexingFileCount?
  )

  @Synchronized
  override fun dispose() {
    // The synchronized block allows to wait for unfinished background dumpers.
    isDisposed = true
  }

}
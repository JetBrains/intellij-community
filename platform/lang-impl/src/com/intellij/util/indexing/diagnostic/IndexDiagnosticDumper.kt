// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectCachePath
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.NonUrgentExecutor
import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumperUtils.indexingDiagnosticDir
import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumperUtils.jacksonMapper
import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumperUtils.oldVersionIndexingDiagnosticDir
import com.intellij.util.indexing.diagnostic.IndexStatisticGroup.IndexingActivityType
import com.intellij.util.indexing.diagnostic.dto.*
import com.intellij.util.indexing.diagnostic.presentation.createAggregateActivityHtml
import com.intellij.util.indexing.diagnostic.presentation.generateHtml
import com.intellij.util.io.createDirectories
import com.intellij.util.io.delete
import com.intellij.util.io.directoryStreamIfExists
import com.intellij.util.io.fileSizeSafe
import org.jetbrains.annotations.TestOnly
import java.io.Reader
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import java.util.function.Supplier
import kotlin.io.path.*
import kotlin.math.min
import kotlin.streams.asSequence

private const val DIAGNOSTIC_LIMIT_OF_FILES_PROPERTY = "intellij.indexes.diagnostics.limit.of.files"

class IndexDiagnosticDumper : Disposable {
  private val indexingActivityHistoryListenerPublisher =
    ApplicationManager.getApplication().messageBus.syncPublisher(ProjectIndexingActivityHistoryListener.TOPIC)

  companion object {
    @JvmStatic
    fun getInstance(): IndexDiagnosticDumper = service()

    private const val FILE_NAME_PREFIX = "diagnostic-"

    @JvmStatic
    val projectIndexingActivityHistoryListenerEpName: ExtensionPointName<ProjectIndexingActivityHistoryListener> =
      ExtensionPointName.create("com.intellij.projectIndexingActivityHistoryListener")

    @JvmStatic
    private val shouldDumpDiagnosticsForInterruptedUpdaters: Boolean
      get() =
        SystemProperties.getBooleanProperty("intellij.indexes.diagnostics.should.dump.for.interrupted.index.updaters", false)

    @JvmStatic
    private val indexingDiagnosticsLimitOfFiles: Int
      get() = SystemProperties.getIntProperty(DIAGNOSTIC_LIMIT_OF_FILES_PROPERTY, 300)

    private fun hasProvidedDiagnosticsLimitOfFilesFromProperty(): Boolean {
      val providedLimitOfFiles = System.getProperty(DIAGNOSTIC_LIMIT_OF_FILES_PROPERTY)
      if (providedLimitOfFiles == null) return false
      try {
        providedLimitOfFiles.toInt()
      }
      catch (ignored: NumberFormatException) {
        return false
      }
      return true
    }

    @JvmStatic
    private val indexingDiagnosticsSizeLimitOfFilesInMiBPerProject: Int
      get() {
        val providedValue = System.getProperty("intellij.indexes.diagnostics.size.limit.of.files.MiB.per.project")
        if (providedValue != null) {
          try {
            return providedValue.toInt()
          }
          catch (ignored: NumberFormatException) {
          }
        }

        return if (hasProvidedDiagnosticsLimitOfFilesFromProperty()) 0 else 10
      }

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
     * [ProjectDumbIndexingHistoryImpl.visibleTimeToAllThreadsTimeRatio], [IndexingFileSetStatistics]
     *
     * This property allows providing more details on those times and ratio in html
     */
    @JvmStatic
    val shouldProvideVisibleAndAllThreadsTimeInfo: Boolean
      get() =
        SystemProperties.getBooleanProperty("intellij.indexes.diagnostics.should.provide.visible.and.all.threads.time.info", false)

    @JvmStatic
    @TestOnly
    var shouldDumpInUnitTestMode: Boolean = false

    @JvmStatic
    val shouldDumpPathsOfFilesIndexedByInfrastructureExtensions: Boolean =
      SystemProperties.getBooleanProperty("intellij.indexes.diagnostics.should.dump.paths.indexed.by.infrastructure.extensions",
                                          ApplicationManagerEx.isInIntegrationTest())

    @JvmStatic
    val shouldPrintInformationAboutChangedDuringIndexingActionFilesInAggregateHtml: Boolean = false

    private val LOG = Logger.getInstance(IndexDiagnosticDumper::class.java)

    fun readJsonIndexingActivityDiagnostic(file: Path): JsonIndexingActivityDiagnostic? {
      return readJsonIndexingActivityDiagnostic { file.bufferedReader() }
    }

    fun readJsonIndexingActivityDiagnostic(supplier: Supplier<Reader>): JsonIndexingActivityDiagnostic? {
      val appInfo = fastReadAppInfo(supplier.get()) ?: return null
      val runtimeInfo = fastReadRuntimeInfo(supplier.get()) ?: return null

      val diagnosticType = fastReadJsonField(supplier.get(), "type", IndexingActivityType::class.java) ?: return null
      val historyClass: Class<out JsonProjectIndexingActivityHistory> = when (diagnosticType) {
        IndexingActivityType.Scanning -> JsonProjectScanningHistory::class.java
        IndexingActivityType.DumbIndexing -> JsonProjectDumbIndexingHistory::class.java
      }
      val history: JsonProjectIndexingActivityHistory = fastReadJsonField(supplier.get(), "projectIndexingActivityHistory", historyClass)
                                                        ?: return null
      return JsonIndexingActivityDiagnostic(appInfo = appInfo, runtimeInfo = runtimeInfo, type = diagnosticType,
                                            projectIndexingActivityHistory = history)
    }

    fun clearDiagnostic() {
      if (indexingDiagnosticDir.exists()) {
        indexingDiagnosticDir.directoryStreamIfExists { dirStream ->
          dirStream.forEach { FileUtil.deleteWithRenaming(it) }
        }
      }
      if (oldVersionIndexingDiagnosticDir.exists()) {
        oldVersionIndexingDiagnosticDir.directoryStreamIfExists { dirStream ->
          dirStream.forEach { FileUtil.deleteWithRenaming(it) }
        }
      }
    }

    fun getProjectDiagnosticDirectory(project: Project): Path {
      val directory = project.getProjectCachePath(indexingDiagnosticDir)
      directory.createDirectories()
      return directory
    }

    private fun getDiagnosticNumberLimitWithinSizeLimit(existingDiagnostics: List<ExistingIndexingActivityDiagnostic>, sizeLimit: Long): Pair<Int, Long> {
      thisLogger().assertTrue(sizeLimit > 0)
      var sizeLimitLevel = sizeLimit
      var number = 0
      for (diagnostic in existingDiagnostics) {
        sizeLimitLevel -= diagnostic.jsonFile.fileSizeSafe()
        sizeLimitLevel -= diagnostic.htmlFile.fileSizeSafe()
        if (sizeLimitLevel <= 0) {
          break
        }
        number++
      }
      return Pair(min(indexingDiagnosticsLimitOfFiles, number), sizeLimitLevel)
    }

    @TestOnly
    fun getDiagnosticNumberLimitWithinSizeLimit(existingDiagnostics: List<ExistingIndexingActivityDiagnostic>): Int =
      getDiagnosticNumberLimitWithinSizeLimit(existingDiagnostics,
                                              indexingDiagnosticsSizeLimitOfFilesInMiBPerProject * 1024 * 1024.toLong()).first

    private fun <T> fastReadJsonField(jsonFile: Path, propertyName: String, type: Class<T>): T? {
      return fastReadJsonField(jsonFile.bufferedReader(), propertyName, type)
    }

    private fun <T> fastReadJsonField(bufferedReader: Reader, propertyName: String, type: Class<T>): T? {
      try {
        bufferedReader.use { reader ->
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

    private fun fastReadAppInfo(jsonFile: Reader): JsonIndexDiagnosticAppInfo? =
      fastReadJsonField(jsonFile, "appInfo", JsonIndexDiagnosticAppInfo::class.java)

    private fun fastReadRuntimeInfo(jsonFile: Reader): JsonRuntimeInfo? =
      fastReadJsonField(jsonFile, "runtimeInfo", JsonRuntimeInfo::class.java)
  }

  private var isDisposed = false

  private val unsavedIndexingActivityHistories = ConcurrentCollectionFactory.createConcurrentIdentitySet<ProjectIndexingActivityHistory>()

  fun onScanningStarted(history: ProjectScanningHistory) {
    runAllListenersSafely(projectIndexingActivityHistoryListenerEpName, indexingActivityHistoryListenerPublisher) {
      onStartedScanning(history)
    }
  }

  fun onScanningFinished(projectScanningHistory: ProjectScanningHistoryImpl) {
    try {
      projectScanningHistory.scanningFinished()
      if (ApplicationManager.getApplication().isUnitTestMode && !shouldDumpInUnitTestMode) {
        return
      }
      if (projectScanningHistory.times.wasInterrupted && !shouldDumpDiagnosticsForInterruptedUpdaters) {
        return
      }
      unsavedIndexingActivityHistories.add(projectScanningHistory)
      NonUrgentExecutor.getInstance().execute { dumpProjectIndexingActivityHistoryToLogSubdirectory(projectScanningHistory) }
    }
     finally {
       runAllListenersSafely(projectIndexingActivityHistoryListenerEpName, indexingActivityHistoryListenerPublisher) {
         onFinishedScanning(projectScanningHistory)
       }
     }
  }

  fun onDumbIndexingStarted(history: ProjectDumbIndexingHistory) {
    runAllListenersSafely(projectIndexingActivityHistoryListenerEpName, indexingActivityHistoryListenerPublisher) {
      onStartedDumbIndexing(history)
    }
  }

  fun onDumbIndexingFinished(projectDumbIndexingHistory: ProjectDumbIndexingHistoryImpl) {
    try {
      if ((ApplicationManager.getApplication().isUnitTestMode && !shouldDumpInUnitTestMode) ||
          projectDumbIndexingHistory.project.isDefault) {
        return
      }
      if (projectDumbIndexingHistory.times.wasInterrupted && !shouldDumpDiagnosticsForInterruptedUpdaters) {
        return
      }
      projectDumbIndexingHistory.indexingFinished()
      projectDumbIndexingHistory.finishTotalUpdatingTime()
      unsavedIndexingActivityHistories.add(projectDumbIndexingHistory)
      NonUrgentExecutor.getInstance().execute { dumpProjectIndexingActivityHistoryToLogSubdirectory(projectDumbIndexingHistory) }
    }
    finally {
      runAllListenersSafely(projectIndexingActivityHistoryListenerEpName, indexingActivityHistoryListenerPublisher) {
        onFinishedDumbIndexing(projectDumbIndexingHistory)
      }
    }
  }

  private fun <T : Any> runAllListenersSafely(extensionPointName: ExtensionPointName<T>, publisher: T, block: T.() -> Unit) {
    val listeners = ProgressManager.getInstance().computeInNonCancelableSection<List<T>, Exception> {
      extensionPointName.extensionList
    }

    for (listener in listeners.asSequence() + publisher) {
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
  private fun dumpProjectIndexingActivityHistoryToLogSubdirectory(projectIndexingActivityHistory: ProjectIndexingActivityHistory) {
    if (!unsavedIndexingActivityHistories.remove(projectIndexingActivityHistory)) {
      return
    }
    try {
      check(!isDisposed)

      val project = projectIndexingActivityHistory.project
      val indexDiagnosticDirectory = getProjectDiagnosticDirectory(project)

      val (diagnosticJson: Path, diagnosticHtml: Path) = getFilesForNewJsonAndHtmlDiagnostics(indexDiagnosticDirectory)

      val jsonIndexingActivityDiagnostic = JsonIndexingActivityDiagnostic(projectIndexingActivityHistory)
      IndexDiagnosticDumperUtils.writeValue(diagnosticJson, jsonIndexingActivityDiagnostic)
      diagnosticHtml.bufferedWriter().use {
        jsonIndexingActivityDiagnostic.generateHtml(it)
      }

      val existingDiagnostics = parseExistingIndexingActivityDiagnostics(indexDiagnosticDirectory)
      val survivedDiagnostics = deleteOutdatedActivityDiagnostics(existingDiagnostics)
      val sharedIndexEvents = SharedIndexDiagnostic.readEvents(project)
      val changedFilesPushedEvents = ChangedFilesPushedDiagnostic.readEvents(project)
      indexDiagnosticDirectory.resolve("report.html").bufferedWriter().use {
        createAggregateActivityHtml(it, project.name, survivedDiagnostics, sharedIndexEvents, changedFilesPushedEvents)
      }
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
      diagnosticJson = IndexDiagnosticDumperUtils.getDumpFilePath(FILE_NAME_PREFIX, nowTime, "json", indexDiagnosticDirectory)
      diagnosticHtml = IndexDiagnosticDumperUtils.getDumpFilePath(FILE_NAME_PREFIX, nowTime, "html", indexDiagnosticDirectory)
      if (!diagnosticJson.exists() && !diagnosticHtml.exists()) {
        break
      }
      nowTime = nowTime.plusNanos(TimeUnit.MILLISECONDS.toNanos(1))
    }
    return diagnosticJson to diagnosticHtml
  }

  private fun deleteOutdatedActivityDiagnostics(existingDiagnostics: List<ExistingIndexingActivityDiagnostic>):
    List<ExistingIndexingActivityDiagnostic> {
    val sortedDiagnostics = existingDiagnostics.sortedByDescending { it.indexingTimes.updatingStart.instant }

    var sizeLimit = indexingDiagnosticsSizeLimitOfFilesInMiBPerProject * 1024 * 1024.toLong()
    val numberLimit: Int
    if (ApplicationManagerEx.isInIntegrationTest()) {
      numberLimit = existingDiagnostics.size
    }
    else if (sizeLimit > 0) {
      val pair = getDiagnosticNumberLimitWithinSizeLimit(existingDiagnostics, sizeLimit)
      numberLimit = pair.first
      sizeLimit = pair.second
    }
    else {
      numberLimit = indexingDiagnosticsLimitOfFiles
    }

    LOG.debug("deleteOutdatedActivityDiagnostics, existing size ${existingDiagnostics.size}; sizeLimit $sizeLimit, " +
              "indexingDiagnosticsLimitOfFiles $indexingDiagnosticsLimitOfFiles, numberLimit $numberLimit")

    val survivedDiagnostics = sortedDiagnostics.take(numberLimit)
    val outdatedDiagnostics = sortedDiagnostics.drop(numberLimit)

    for (diagnostic in outdatedDiagnostics) {
      diagnostic.jsonFile.delete()
      diagnostic.htmlFile.delete()
    }
    return survivedDiagnostics
  }

  private fun parseExistingIndexingActivityDiagnostics(indexDiagnosticDirectory: Path): List<ExistingIndexingActivityDiagnostic> =
    Files.list(indexDiagnosticDirectory).use { files ->
      files.asSequence()
        .filter { file -> file.fileName.toString().startsWith(FILE_NAME_PREFIX) && file.extension == "json" }
        .mapNotNull { jsonFile ->
          val appInfo = fastReadAppInfo(jsonFile.bufferedReader()) ?: return@mapNotNull null
          val runtimeInfo = fastReadRuntimeInfo(jsonFile.bufferedReader()) ?: return@mapNotNull null

          val htmlFile = jsonFile.resolveSibling(jsonFile.nameWithoutExtension + ".html")
          if (!htmlFile.exists()) {
            return@mapNotNull null
          }

          val diagnosticType = fastReadJsonField(jsonFile, "type", IndexingActivityType::class.java) ?: return@mapNotNull null

          val times: JsonProjectIndexingActivityHistoryTimes
          val fileCount: JsonProjectIndexingActivityFileCount
          when (diagnosticType) {
            IndexingActivityType.Scanning -> {
              times = fastReadJsonField(jsonFile, "times", JsonProjectScanningHistoryTimes::class.java) ?: return@mapNotNull null
              fileCount = fastReadJsonField(jsonFile, "fileCount", JsonProjectScanningFileCount::class.java) ?: return@mapNotNull null
            }
            IndexingActivityType.DumbIndexing -> {
              times = fastReadJsonField(jsonFile, "times", JsonProjectDumbIndexingHistoryTimes::class.java) ?: return@mapNotNull null
              fileCount = fastReadJsonField(jsonFile, "fileCount", JsonProjectDumbIndexingFileCount::class.java) ?: return@mapNotNull null
            }
          }

          ExistingIndexingActivityDiagnostic(jsonFile, htmlFile, appInfo, runtimeInfo, diagnosticType, times, fileCount)
        }
        .toList()
    }

  data class ExistingIndexingActivityDiagnostic(
    val jsonFile: Path,
    val htmlFile: Path,
    val appInfo: JsonIndexDiagnosticAppInfo,
    val runtimeInfo: JsonRuntimeInfo,
    val type: IndexingActivityType,
    val indexingTimes: JsonProjectIndexingActivityHistoryTimes,
    val fileCount: JsonProjectIndexingActivityFileCount
  )

  @Synchronized
  override fun dispose() {
    // it's important to save diagnostic, no matter how
    for (unsavedIndexingActivityHistory in unsavedIndexingActivityHistories) {
      dumpProjectIndexingActivityHistoryToLogSubdirectory(unsavedIndexingActivityHistory)
    }
    // The synchronized block allows waiting for unfinished background dumpers.
    isDisposed = true
  }
}

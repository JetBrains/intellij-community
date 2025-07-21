// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.intellij.idea.TestFor
import com.intellij.openapi.project.getProjectCachePath
import com.intellij.psi.PsiManager
import com.intellij.testFramework.IndexingTestUtil.Companion.waitUntilIndexesAreReady
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.TestModeFlags
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.testFramework.registerExtension
import com.intellij.util.application
import com.intellij.util.indexing.diagnostic.*
import com.intellij.util.indexing.diagnostic.dto.*
import com.intellij.util.indexing.diagnostic.dump.paths.PortableFilePath
import org.junit.Assert
import java.io.BufferedReader
import java.io.StringReader
import java.nio.file.Files
import java.time.ZonedDateTime
import kotlin.io.path.createTempDirectory
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * Tests for [IndexDiagnosticDumper].
 */
class IndexDiagnosticTest : JavaCodeInsightFixtureTestCase() {

  @TestFor(issues = ["IDEA-252012"])
  fun `test index diagnostics are laid out per project`() {
    try {
      val tempDirectory = createTempDirectory()
      TestModeFlags.set(IndexDiagnosticDumperUtils.testDiagnosticPathFlag, tempDirectory, testRootDisposable)
      IndexDiagnosticDumper.shouldDumpInUnitTestMode = true

      val externalDir = myFixture.tempDirFixture.createFile("dir/A.java", "class A { void m() { } }").parent
      PsiTestUtil.addContentRoot(myFixture.module, externalDir)
      PsiManager.getInstance(project).dropPsiCaches()
      waitUntilIndexesAreReady(project)
      IndexDiagnosticDumper.getInstance().waitAllActivitiesAreDumped()

      val allDirs = Files.list(IndexDiagnosticDumperUtils.indexingDiagnosticDir).use { it.toList() }
      val projectDir = myFixture.project.getProjectCachePath(IndexDiagnosticDumperUtils.indexingDiagnosticDir)
      assertEquals(listOf(projectDir), allDirs)

      for (dir in allDirs) {
        val files = Files.list(dir).use { it.toList() }.filter {
          it.fileName.name != "changed-files-pushing-events.json" &&
          it.fileName.name != "report.html"
        }
        val jsonFiles = files.filter { it.extension == "json" }
        val htmlFiles = files.filter { it.extension == "html" }
        assertTrue(files.isNotEmpty())
        assertEquals(files.joinToString(prefix = "Actual files: ") { it.toString() }, files.size, jsonFiles.size + htmlFiles.size)
        assertEquals(files.joinToString(prefix = "Actual files: ") { it.toString() },
                     jsonFiles.map { it.nameWithoutExtension }.toSet(),
                     htmlFiles.map { it.nameWithoutExtension }.toSet())
      }
    }
    finally {
      IndexDiagnosticDumper.shouldDumpInUnitTestMode = false
    }
  }

  fun `test empty index diagnostic with default fields can be deserialized`() {
    val mapper = jacksonObjectMapper().registerKotlinModule()

    val indexDiagnostic = JsonIndexingActivityDiagnostic()
    println(mapper.writeValueAsString(indexDiagnostic))

    val deserialized = deserializeDiagnostic(mapper, indexDiagnostic)
    Assert.assertEquals(indexDiagnostic, deserialized)
  }

  fun `test scanning diagnostics json can be deserialized`() {
    val indexDiagnostic = JsonIndexingActivityDiagnostic(
      JsonIndexDiagnosticAppInfo.create(),
      JsonRuntimeInfo.create(),
      IndexStatisticGroup.IndexingActivityType.Scanning,
      JsonProjectScanningHistory(
        projectName = "projectName",
        times = JsonProjectScanningHistoryTimes(
          "reason",
          ScanningType.PARTIAL,
          12,
          JsonDuration(123),
          JsonDuration(456),
          JsonDuration(789),
          JsonDuration(110),
          JsonDuration(234),
          JsonDuration(345),
          JsonDuration(345),
          JsonDuration(356),
          JsonDateTime(ZonedDateTime.now()),
          JsonDuration(935),
          JsonDuration(346),
          JsonDateTime(ZonedDateTime.now()),
          JsonDateTime(ZonedDateTime.now()),
          JsonDuration(222),
          JsonDuration(244),
          false
        ),
        fileCount = JsonProjectScanningFileCount(
          numberOfFileProviders = 0,
          numberOfScannedFiles = 0,
          numberOfFilesIndexedByInfrastructureExtensionsDuringScan = 0,
          numberOfFilesScheduledForIndexingAfterScan = 0,
        ),
        scanningStatistics = listOf(
          JsonScanningStatistics(
            "providerName",
            333,
            11,
            55,
            33,
            filesFullyIndexedByInfrastructureExtensions = listOf(
              PortableFilePath.RelativePath(PortableFilePath.ProjectRoot, "src/a.java").presentablePath),
            JsonDuration(123),
            JsonDuration(456),
            JsonDuration(789),
            JsonDuration(222),
            roots = listOf("<project root>"),
            scannedFiles = listOf(
              JsonScanningStatistics.JsonScannedFile(
                path = PortableFilePath.RelativePath(PortableFilePath.ProjectRoot, "src/a.java"),
                isUpToDate = true,
                wasFullyIndexedByInfrastructureExtension = false
              )
            )
          )
        )
      )
    )

    val mapper = jacksonObjectMapper().registerKotlinModule()

    val deserialized = deserializeDiagnostic(mapper, indexDiagnostic)
    Assert.assertEquals(indexDiagnostic, deserialized)
  }

  fun `test dumb indexing diagnostics json can be deserialized`() {
    val indexDiagnostic = JsonIndexingActivityDiagnostic(
      JsonIndexDiagnosticAppInfo.create(),
      JsonRuntimeInfo.create(),
      IndexStatisticGroup.IndexingActivityType.DumbIndexing,
      JsonProjectDumbIndexingHistory(
        projectName = "projectName",
        times = JsonProjectDumbIndexingHistoryTimes(
          setOf(3, 4),
          JsonDuration(123),
          JsonDuration(456),
          JsonDuration(789),
          JsonDateTime(ZonedDateTime.now()),
          JsonDateTime(ZonedDateTime.now()),
          JsonDuration(110),
          JsonDuration(234),
          false
        ),
        fileCount = JsonProjectDumbIndexingFileCount(
          numberOfFilesIndexedByInfrastructureExtensionsDuringIndexingStage = 0,
          numberOfFilesIndexedWithLoadingContent = 0,
          numberOfChangedDuringIndexingFiles = 0,
          numberOfNothingToWriteFiles = 0,
        ),
        totalStatsPerFileType = listOf(
          JsonProjectDumbIndexingHistory.JsonStatsPerFileType(
            "java",
            JsonPercentages(30, 100),
            JsonPercentages(40, 100),
            22,
            JsonFileSize(333),
            JsonProcessingSpeed(444, 555),
            listOf(
              JsonProjectDumbIndexingHistory.JsonStatsPerFileType.JsonBiggestFileTypeContributor(
                "providerName",
                444,
                JsonFileSize(555),
                JsonPercentages(8, 10)
              )
            )
          )
        ),
        totalStatsPerIndexer = listOf(
          JsonProjectDumbIndexingHistory.JsonStatsPerIndexer(
            "IdIndex",
            shardsCount = 1,
            JsonPercentages(5, 10),
            444,
            555,
            JsonFileSize(123),
            JsonProcessingSpeed(111, 222),
          )
        ),
        fileProviderStatistics = listOf(
          JsonFileProviderIndexStatistics(
            providerName = "providerName",
            totalNumberOfIndexedFiles = 444,
            totalNumberOfFilesFullyIndexedByExtensions = 33,
            totalNumberOfNothingToWriteFiles = 15,
            totalIndexingVisibleTime = JsonDuration(123),
            contentLoadingVisibleTime = JsonDuration(456),
            numberOfTooLargeForIndexingFiles = 1,
            slowIndexedFiles = listOf(
              JsonFileProviderIndexStatistics.JsonSlowIndexedFile(
                "file",
                JsonDuration(123),
                JsonDuration(456),
                JsonDuration(789)
              )
            ),
            indexedFiles = listOf(
              JsonFileProviderIndexStatistics.JsonIndexedFile(
                path = PortableFilePath.RelativePath(PortableFilePath.ProjectRoot, "src/a.java"),
                wasFullyIndexedByExtensions = true,
                nothingToWrite = false,
              )
            ),
            separateApplyingIndexesVisibleTime = JsonDuration(362)
          )
        )
      )
    )

    val mapper = jacksonObjectMapper().registerKotlinModule()

    val deserialized = deserializeDiagnostic(mapper, indexDiagnostic)
    Assert.assertEquals(indexDiagnostic, deserialized)
  }

  fun `test scanning diagnostics handles exceptions in listeners`() {
    val faultyListener = object : ProjectIndexingActivityHistoryListener {
      override fun onStartedScanning(history: ProjectScanningHistory) {
        throw AssertionError("test error")
      }

      override fun onFinishedScanning(history: ProjectScanningHistory) {
        throw AssertionError("test error")
      }

      override fun onStartedDumbIndexing(history: ProjectDumbIndexingHistory) {
        throw AssertionError("test error")
      }

      override fun onFinishedDumbIndexing(history: ProjectDumbIndexingHistory) {
        throw AssertionError("test error")
      }
    }
    application.registerExtension(IndexDiagnosticDumper.projectIndexingActivityHistoryListenerEpName, faultyListener, testRootDisposable)
    // Registration of extension will trigger indexing, but this is implicit knowledge. Invoke full indexing explicitly.
    UnindexedFilesScanner(project).queue()
    waitUntilIndexesAreReady(project, 10.seconds.toJavaDuration())

    // Repeat.
    // If the previous scanning request has stopped ScanningExecutorService with an exception,
    // the following waitUntilIndexesAreReady should time out
    UnindexedFilesScanner(project).queue()
    waitUntilIndexesAreReady(project, 10.seconds.toJavaDuration())

    // should not throw, should not time out
  }

  private fun deserializeDiagnostic(mapper: ObjectMapper, indexDiagnostic: JsonIndexingActivityDiagnostic) =
    IndexDiagnosticDumper.readJsonIndexingActivityDiagnostic {
      BufferedReader(StringReader(mapper.writeValueAsString(indexDiagnostic)))
    }
}

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.intellij.idea.TestFor
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.getProjectCachePath
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.util.SystemProperties
import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumper
import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumperUtils
import com.intellij.util.indexing.diagnostic.IndexStatisticGroup
import com.intellij.util.indexing.diagnostic.ScanningType
import com.intellij.util.indexing.diagnostic.dto.*
import com.intellij.util.indexing.diagnostic.dump.paths.PortableFilePath
import org.junit.Assert
import java.io.BufferedReader
import java.io.StringReader
import java.nio.file.Files
import java.time.ZonedDateTime
import kotlin.io.path.createTempDirectory

/**
 * Tests for [IndexDiagnosticDumper].
 */
class IndexDiagnosticTest : JavaCodeInsightFixtureTestCase() {
  private var previousLogDir: String? = null

  override fun setUp() {
    previousLogDir = System.setProperty(PathManager.PROPERTY_LOG_PATH, createTempDirectory().toString())
    IndexDiagnosticDumper.shouldDumpInUnitTestMode = true
    super.setUp()
  }

  override fun tearDown() {
    try {
      IndexDiagnosticDumper.shouldDumpInUnitTestMode = false
      SystemProperties.setProperty(PathManager.PROPERTY_LOG_PATH, previousLogDir)
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  @TestFor(issues = ["IDEA-252012"])
  fun `test index diagnostics are laid out per project`() {
    myFixture.addFileToProject("A.java", "class A { void m() { } }")
    val indexingDiagnosticDir = IndexDiagnosticDumperUtils.indexingDiagnosticDir
    val allDirs = Files.list(indexingDiagnosticDir).use { it.toList() }
    val projectDir = myFixture.project.getProjectCachePath(IndexDiagnosticDumperUtils.indexingDiagnosticDir)
    assertEquals(listOf(projectDir), allDirs)
/*
    for (dir in allDirs) {
      val files = Files.list(dir).use { it.toList() }
      val jsonFiles = files.filter { it.extension == "json" }
      val htmlFiles = files.filter { it.extension == "html" }
      assertTrue(files.isNotEmpty())
      assertEquals(files.joinToString { it.toString() }, files.size, jsonFiles.size + htmlFiles.size)
      assertEquals(files.joinToString { it.toString() }, jsonFiles.map { it.nameWithoutExtension }.toSet(), htmlFiles.map { it.nameWithoutExtension }.toSet())
    }
*/
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

  private fun deserializeDiagnostic(mapper: ObjectMapper, indexDiagnostic: JsonIndexingActivityDiagnostic) =
    IndexDiagnosticDumper.readJsonIndexingActivityDiagnostic {
      BufferedReader(StringReader(mapper.writeValueAsString(indexDiagnostic)))
    }
}

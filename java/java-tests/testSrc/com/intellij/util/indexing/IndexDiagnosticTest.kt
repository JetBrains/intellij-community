// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.intellij.idea.TestFor
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.getProjectCachePath
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumper
import com.intellij.util.indexing.diagnostic.dto.*
import com.intellij.util.indexing.diagnostic.dump.paths.PortableFilePath
import org.junit.Assert
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.ZonedDateTime
import kotlin.streams.toList

/**
 * Tests for [IndexDiagnosticDumper].
 */
class IndexDiagnosticTest : JavaCodeInsightFixtureTestCase() {

  private var previousLogDir: Path? = null

  override fun setUp() {
    previousLogDir = System.getProperty(PathManager.PROPERTY_LOG_PATH)?.let { Paths.get(it) }
    val tempLogDir = createTempDir().toPath()
    System.setProperty(PathManager.PROPERTY_LOG_PATH, tempLogDir.toAbsolutePath().toString())
    IndexDiagnosticDumper.shouldDumpInUnitTestMode = true
    super.setUp()
  }

  override fun tearDown() {
    super.tearDown()
    IndexDiagnosticDumper.shouldDumpInUnitTestMode = false
    if (previousLogDir == null) {
      System.clearProperty(PathManager.PROPERTY_LOG_PATH)
    }
    else {
      System.setProperty(PathManager.PROPERTY_LOG_PATH, previousLogDir!!.toAbsolutePath().toString())
    }
  }

  @TestFor(issues = ["IDEA-252012"])
  fun `test index diagnostics are laid out per project`() {
    myFixture.addFileToProject("A.java", "class A { void m() { } }")
    val indexingDiagnosticDir = IndexDiagnosticDumper.indexingDiagnosticDir
    val allDirs = Files.list(indexingDiagnosticDir).use { it.toList() }
    val projectDir = myFixture.project.getProjectCachePath(IndexDiagnosticDumper.indexingDiagnosticDir)
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

  fun `test index diagnostics json can be deserialized`() {
    val indexDiagnostic = JsonIndexDiagnostic(
      JsonIndexDiagnosticAppInfo.create(),
      JsonRuntimeInfo.create(),
      JsonProjectIndexingHistory(
        projectName = "projectName",
        times = JsonProjectIndexingHistoryTimes(
          JsonDuration(123),
          JsonDuration(456),
          JsonDuration(789),
          JsonDuration(234),
          JsonDuration(345),
          JsonDateTime(ZonedDateTime.now()),
          JsonDateTime(ZonedDateTime.now()),
          JsonDuration(333),
          false
        ),
        totalStatsPerFileType = listOf(
          JsonProjectIndexingHistory.JsonStatsPerFileType(
            "java",
            JsonPercentages(30, 100),
            JsonPercentages(40, 100),
            22,
            JsonFileSize(333),
            JsonProcessingSpeed(444, 555),
            listOf(
              JsonProjectIndexingHistory.JsonStatsPerFileType.JsonBiggestFileTypeContributor(
                "providerName",
                444,
                JsonFileSize(555),
                JsonPercentages(8, 10)
              )
            )
          )
        ),
        totalStatsPerIndexer = listOf(
          JsonProjectIndexingHistory.JsonStatsPerIndexer(
            "IdIndex",
            JsonPercentages(5, 10),
            444,
            555,
            JsonFileSize(123),
            JsonProcessingSpeed(111, 222),
            JsonProjectIndexingHistory.JsonStatsPerIndexer.JsonSnapshotInputMappingStats(33, 44, 55)
          )
        ),
        scanningStatistics = listOf(
          JsonScanningStatistics(
            "providerName",
            333,
            11,
            55,
            33,
            JsonDuration(123),
            JsonDuration(456),
            JsonDuration(789),
            JsonDuration(222),
            scannedFiles = listOf(
              JsonScanningStatistics.JsonScannedFile(
                path = PortableFilePath.RelativePath (PortableFilePath.ProjectRoot, "src/a.java"),
                isUpToDate = true,
                wasFullyIndexedByInfrastructureExtension = false
              )
            )
          )
        ),
        fileProviderStatistics = listOf(
          JsonFileProviderIndexStatistics(
            "providerName",
            444,
            33,
            JsonDuration(123),
            1,
            listOf(
              JsonFileProviderIndexStatistics.JsonIndexedFile(
                path = PortableFilePath.RelativePath(PortableFilePath.ProjectRoot, "src/a.java"),
                wasFullyIndexedByExtensions = true
              )
            )
          )
        )
      )
    )

    val mapper = jacksonObjectMapper().registerKotlinModule()

    println(mapper.writeValueAsString(indexDiagnostic))

    val deserialized = mapper.readValue<JsonIndexDiagnostic>(mapper.writeValueAsString(indexDiagnostic))
    Assert.assertEquals(indexDiagnostic, deserialized)
  }
}
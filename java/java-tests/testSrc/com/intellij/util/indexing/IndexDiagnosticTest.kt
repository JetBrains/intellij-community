// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing

import com.intellij.idea.TestFor
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.getProjectCachePath
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumper
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
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
    IndexDiagnosticDumper.shouldDumpInUnitTestMode = false
    if (previousLogDir == null) {
      System.clearProperty(PathManager.PROPERTY_LOG_PATH)
    }
    else {
      System.setProperty(PathManager.PROPERTY_LOG_PATH, previousLogDir!!.toAbsolutePath().toString())
    }
    super.tearDown()
  }

  @TestFor(issues = ["IDEA-252012"])
  fun `test index diagnostics are laid out per project`() {
    myFixture.addFileToProject("A.java", "class A { void m() { } }")
    val indexingDiagnosticDir = IndexDiagnosticDumper.indexingDiagnosticDir
    val allDirs = Files.list(indexingDiagnosticDir).use { it.toList() }
    val projectDir = myFixture.project.getProjectCachePath(IndexDiagnosticDumper.indexingDiagnosticDir)
    assertEquals(listOf(projectDir), allDirs)
  }
}
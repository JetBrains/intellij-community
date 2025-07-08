// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.coverage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.BuildPaths.Companion.ULTIMATE_HOME
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.TestingOptions
import org.jetbrains.intellij.build.TestingTasks
import org.jetbrains.intellij.build.impl.CompilationContextImpl.Companion.createCompilationContext
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.walk

class CoverageTest {
  @TempDir
  lateinit var tempDir: Path
  private val coverageFlag: String = CoverageTest::class.java.canonicalName
  private val isAlreadyUnderCoverage: Boolean = System.getProperty(coverageFlag) == "true"
  private val coveredClass: Class<*> = TestingOptions::class.java
  private val testingOptions: TestingOptions = TestingOptions().apply {
    enableCoverage = true
    coveredClassesPatterns = coveredClass.packageName + ".*"
    testPatterns = CoverageTest::class.java.canonicalName
    mainModule = "intellij.platform.buildScripts.tests"
  }

  private suspend fun context(): CompilationContext = createCompilationContext(
    projectHome = ULTIMATE_HOME,
    buildOutputRootEvaluator = { tempDir },
    setupTracer = false,
    enableCoroutinesDump = false,
    options = BuildOptions().apply {
      useCompiledClassesFromProjectOutput = true
    }
  )

  @Test
  fun test() {
    runBlocking(Dispatchers.Default) {
      val tests = TestingTasks.create(context(), testingOptions)
      if (isAlreadyUnderCoverage) return@runBlocking
      tests.runTests(additionalSystemProperties = mapOf(coverageFlag to "true"))
      assertCoverage(coveredClassName = coveredClass.simpleName, reportDir = tests.coverage.reportDir)
    }
  }

  private fun assertCoverage(coveredClassName: String, reportDir: Path) {
    assert(
      reportDir.walk()
        .filter { it.name.startsWith("source-") && it.extension == "html" }
        .map { Jsoup.parse(it.readText()).body() }
        .flatMap { it.getElementsByClass("coverageStats") }
        .flatMap { it.getElementsByTag("tbody") }
        .flatMap { it.getElementsByTag("tr") }
        .filter { tableTow ->
          tableTow.getElementsByClass("name").any {
            it.text().trim() == coveredClassName
          }
        }.filter { tableTow ->
          tableTow.getElementsByClass("percent").any {
            it.text().trim().trimEnd('%').toInt() > 0
          }
        }.any()
    ) {
      "$coveredClassName is expected to be called by this test class but 0% coverage is detected"
    }
  }

  @Test
  fun `test aggregating reports`() {
    if (isAlreadyUnderCoverage) return
    runBlocking(Dispatchers.Default) {
      val tests = TestingTasks.create(context(), testingOptions)
      tests.coverage.aggregateAndReport((1..2).map {
        tempDir.resolve("coverage-report-$it.ic").createFile()
      })
    }
  }
}

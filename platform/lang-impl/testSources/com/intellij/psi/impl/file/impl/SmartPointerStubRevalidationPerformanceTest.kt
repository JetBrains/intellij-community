// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.multiverseProjectFixture
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.SkipSlowTestLocally
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.StressTestApplication
import com.intellij.testFramework.junit5.fixture.fileOrDirInProjectFixture
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * A benchmark for the lazy revalidation of the stub-based pointers of one file.
 *
 * A stub-based [com.intellij.psi.impl.smartPointers.AnchorElementInfo] has no range. Such an info
 * only entered the scan with IJPL-256519, so it has no other benchmark.
 *
 * See `smartPointerBenchmarks.kt` for the rules a body must follow, and for the other three
 * benchmark classes.
 */
@SkipSlowTestLocally
@StressTestApplication
@PerformanceUnitTest
internal class SmartPointerStubRevalidationPerformanceTest {
  private val projectFixture = multiverseProjectFixture(withSharedSourceEnabled = true, openAfterCreation = true) {
    module("stubs") {
      contentRoot("contentRoot") {
        sourceRoot("src") {
          file("Stubs.java", stubFileText(STUB_COUNT))
        }
      }
    }
  }

  private val project by projectFixture
  private val stubsFile by projectFixture.fileOrDirInProjectFixture("stubs/contentRoot/src/Stubs.java")

  // Strong references. The tracker and the pointers are reachable from here only.
  private lateinit var psiFile: PsiFileImpl
  private val stubPointers = mutableListOf<SmartPsiElementPointer<PsiElement>>()

  /**
   * The same shape as
   * [SmartPointerRevalidationPerformanceTest.benchmarkLazyRevalidationUnderRepeatedBumps], but with
   * range-less infos.
   *
   * The body resolves through `element` only. `range` and `psiRange` switch the anchor to the tree,
   * which would make the later rounds measure a different thing. The test asserts that the file
   * holds no tree before and after the measurement.
   */
  @Test
  fun benchmarkStubPointerRevalidation() {
    timeoutRunBlocking(timeout = BENCHMARK_PREPARE_TIMEOUT) { prepareStubPointers() }

    Benchmark.newBenchmark("smart pointer lazy revalidation of stub-based pointers") {
      runWriteActionAndWait {
        var resolved = 0
        repeat(BUMP_COUNT) { round ->
          project.bumpSmartPointerGeneration()
          if (stubPointers[round % STUB_COUNT].element != null) resolved++
        }
        assertEquals(BUMP_COUNT, resolved, "Every resolution after a bump must succeed")
      }
    }
      .setup { runReadActionBlocking { stubPointers[0].element } }
      .attempts(BENCHMARK_ATTEMPTS)
      .start("${javaClass.name}.benchmarkStubPointerRevalidation")

    assertEquals(STUB_COUNT, project.smartPointersNumber(psiFile),
                 "The garbage collector removed pointers during the benchmark")
    assertNull(psiFile.treeElement,
               "The benchmark switched the stubs to a tree, so it did not measure range-less infos")
  }

  // --- preparation ---

  private suspend fun prepareStubPointers() {
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    psiFile = readAction { PsiManager.getInstance(project).findFile(stubsFile) as PsiFileImpl }
    readAction {
      val methods = psiFile.stubTree!!.plainList
        .map { it.psi }
        .filter { it is PsiNamedElement && it.name?.startsWith(METHOD_PREFIX) == true }
      assertEquals(STUB_COUNT, methods.size, "The stub tree must hold every method")
      methods.forEach { stubPointers += SmartPointerManager.createPointer(it) }
      assertNull(psiFile.treeElement, "The pointers must stay stub-based")
      stubPointers.forEach { it.element }
    }
    assertEquals(STUB_COUNT, project.smartPointersNumber(psiFile))
  }

  private companion object {
    const val STUB_COUNT: Int = 2_000
    const val BUMP_COUNT: Int = 5_000
    const val METHOD_PREFIX: String = "method"

    fun stubFileText(methodCount: Int): String =
      (0 until methodCount).joinToString("\n", prefix = "class Stubs {\n", postfix = "\n}") {
        "  void $METHOD_PREFIX$it() {}"
      }
  }
}

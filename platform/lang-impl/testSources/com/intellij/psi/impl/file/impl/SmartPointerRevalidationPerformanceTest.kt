// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.multiverseProjectFixture
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.SmartPsiFileRange
import com.intellij.psi.impl.smartPointers.SmartPointerManagerEx
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.SkipSlowTestLocally
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.StressTestApplication
import com.intellij.testFramework.junit5.fixture.fileOrDirInProjectFixture
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * Benchmarks for the lazy revalidation of the range pointers of one file in one context.
 *
 * See `smartPointerBenchmarks.kt` for the rules a body must follow, and for the other three
 * benchmark classes.
 */
@SkipSlowTestLocally
@StressTestApplication
@PerformanceUnitTest
internal class SmartPointerRevalidationPerformanceTest {
  private val projectFixture = multiverseProjectFixture(withSharedSourceEnabled = true, openAfterCreation = true) {
    module("revalidation") {
      contentRoot("contentRoot") {
        sourceRoot("src") {
          file("scan.txt", blankText(RANGE_COUNT + 1))
          file("resolve.txt", blankText(RANGE_COUNT + 1))
          file("mappings.txt", blankText(RANGE_COUNT + 1))
        }
      }
    }
  }

  private val project by projectFixture
  private val scanFile by projectFixture.fileOrDirInProjectFixture("revalidation/contentRoot/src/scan.txt")
  private val resolveFile by projectFixture.fileOrDirInProjectFixture("revalidation/contentRoot/src/resolve.txt")
  private val mappingsFile by projectFixture.fileOrDirInProjectFixture("revalidation/contentRoot/src/mappings.txt")

  // Strong references. The tracker and the pointers are reachable from here only.
  private lateinit var psiFile: PsiFile
  private val rangePointers = mutableListOf<SmartPsiFileRange>()
  private var filePointer: SmartPsiElementPointer<PsiFile>? = null
  private val syntheticMappings = mutableListOf<Map<CodeInsightContext, CodeInsightContext?>>()

  /**
   * One file, many range pointers, and one bump of the generation counter per round. Each round
   * resolves exactly one pointer, so each round pays exactly one full scan.
   *
   * This is the isolated number for the scan. It bumps the counter directly, so it does not include
   * the walk of the view provider cache. See
   * [SmartPointerBulkInvalidationPerformanceTest.benchmarkBulkInvalidationManyFiles] for the
   * end-to-end number.
   */
  @Test
  fun benchmarkLazyRevalidationUnderRepeatedBumps() {
    timeoutRunBlocking(timeout = BENCHMARK_PREPARE_TIMEOUT) { prepareRangePointers(scanFile) }

    Benchmark.newBenchmark("smart pointer lazy revalidation, repeated generation bumps") {
      runWriteActionAndWait {
        var resolved = 0
        repeat(BUMP_COUNT) { round ->
          project.bumpSmartPointerGeneration()
          if (rangePointers[round % RANGE_COUNT].element != null) resolved++
        }
        assertEquals(BUMP_COUNT, resolved, "Every resolution after a bump must succeed")
      }
    }
      .setup { runReadActionBlocking { rangePointers[0].element } }
      .attempts(BENCHMARK_ATTEMPTS)
      .start("${javaClass.name}.benchmarkLazyRevalidationUnderRepeatedBumps")

    assertEquals(RANGE_COUNT, project.smartPointersNumber(psiFile),
                 "The garbage collector removed pointers during the benchmark")
    runReadActionBlocking {
      assertNotNull(filePointer!!.element, "The file pointer must still resolve")
      assertEquals(RANGE_COUNT, rangePointers.count { it.range != null }, "Every range must still resolve")
    }
  }

  /**
   * Two launches over one pointer set, to separate the scan from the resolution.
   *
   * `steady state` resolves every pointer with no bump, so it pays no scan. `after generation bump`
   * does the same work plus one scan per round. Neither number means anything alone, because both
   * include `PsiElement.isValid` for every pointer. The difference is the cost of the scan.
   */
  @Test
  fun benchmarkResolveAll() {
    timeoutRunBlocking(timeout = BENCHMARK_PREPARE_TIMEOUT) { prepareRangePointers(resolveFile) }

    Benchmark.newBenchmark("smart pointer resolve-all steady state, no generation bump") {
      runReadActionBlocking {
        val generation = SmartPointerManagerEx.getInstanceEx(project).possiblyInvalidationModCounter.modificationCount
        repeat(RESOLVE_ROUNDS) {
          assertEquals(RANGE_COUNT, rangePointers.count { it.element != null })
        }
        assertEquals(generation,
                     SmartPointerManagerEx.getInstanceEx(project).possiblyInvalidationModCounter.modificationCount,
                     "A resolution must not move the generation")
      }
    }
      .attempts(BENCHMARK_ATTEMPTS)
      .start("${javaClass.name}.benchmarkResolveAll")

    Benchmark.newBenchmark("smart pointer resolve-all after generation bump") {
      runWriteActionAndWait {
        repeat(RESOLVE_ROUNDS) {
          project.bumpSmartPointerGeneration()
          assertEquals(RANGE_COUNT, rangePointers.count { it.element != null })
        }
      }
    }
      .attempts(BENCHMARK_ATTEMPTS)
      .start("${javaClass.name}.benchmarkResolveAll")

    assertEquals(RANGE_COUNT, project.smartPointersNumber(psiFile),
                 "The garbage collector removed pointers during the benchmark")
  }

  /**
   * The composition of the queued context mappings.
   *
   * `MultiverseFileViewProviderCache` queues one mapping per context reassignment, and the next
   * resolution composes the whole queue. The composition is quadratic in the length of the queue.
   *
   * The benchmark queues the mappings through a test-only entry point. A real context change costs
   * milliseconds, which would hide the composition completely.
   *
   * The mappings are a chain of synthetic contexts. No pointer belongs to them, so the composed
   * mapping changes no file holder and every pointer must still resolve.
   */
  @Test
  fun benchmarkPendingMappingComposition() {
    timeoutRunBlocking(timeout = BENCHMARK_PREPARE_TIMEOUT) {
      prepareRangePointers(mappingsFile)
      prepareSyntheticMappings()
    }

    Benchmark.newBenchmark("smart pointer pending context mapping composition") {
      runWriteActionAndWait {
        val tracker = SmartPointerManagerEx.getInstanceEx(project).getTracker(mappingsFile)!!
        for (mapping in syntheticMappings) {
          tracker.pushContextMappingForTests(mapping)
        }
        project.bumpSmartPointerGeneration()
        // One resolution composes the whole queue and then drains it.
        val resolved = rangePointers[0].element
        assertNotNull(resolved, "The pointer must resolve after the composition")
        assertSame(resolved, rangePointers[0].element, "The queue must be drained after one resolution")
      }
    }
      .attempts(BENCHMARK_ATTEMPTS)
      .start("${javaClass.name}.benchmarkPendingMappingComposition")

    assertEquals(RANGE_COUNT, project.smartPointersNumber(psiFile),
                 "The garbage collector removed pointers during the benchmark")
  }

  // --- preparation ---

  private suspend fun prepareRangePointers(vFile: VirtualFile) {
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    psiFile = readAction { PsiManager.getInstance(project).findFile(vFile)!! }
    readAction {
      val manager = SmartPointerManagerEx.getInstanceEx(project)
      // Arithmetic offsets. A search per pointer would dominate the preparation.
      repeat(RANGE_COUNT) { i ->
        rangePointers += manager.createSmartPsiFileRangePointer(psiFile, TextRange(i, i + 1))
      }
      filePointer = SmartPointerManager.createPointer(psiFile)
      // Resolve one time, so the file holders are interned before the measurement.
      rangePointers.forEach { it.element }
    }
    assertEquals(RANGE_COUNT, project.smartPointersNumber(psiFile))
  }

  private fun prepareSyntheticMappings() {
    val contexts = List(MAPPING_COUNT + 1) { SyntheticContext(it) }
    // A chain: each value is the key of the next mapping. This is the worst case for the composition.
    repeat(MAPPING_COUNT) { i ->
      syntheticMappings += mapOf<CodeInsightContext, CodeInsightContext?>(contexts[i] to contexts[i + 1])
    }
  }

  private companion object {
    const val RANGE_COUNT: Int = 20_000
    const val BUMP_COUNT: Int = 1_000
    const val RESOLVE_ROUNDS: Int = 25
    const val MAPPING_COUNT: Int = 5_000
  }
}

private class SyntheticContext(private val index: Int) : CodeInsightContext {
  override fun toString(): String = "SyntheticContext($index)"
}

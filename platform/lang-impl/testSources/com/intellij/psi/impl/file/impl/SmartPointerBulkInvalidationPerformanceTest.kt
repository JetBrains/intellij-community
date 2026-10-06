// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtil
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A benchmark for the end-to-end cost of a bulk invalidation of a project with many files.
 *
 * See `smartPointerBenchmarks.kt` for the rules a body must follow, and for the other three
 * benchmark classes.
 */
@SkipSlowTestLocally
@StressTestApplication
@PerformanceUnitTest
internal class SmartPointerBulkInvalidationPerformanceTest {
  private val projectFixture = multiverseProjectFixture(withSharedSourceEnabled = true, openAfterCreation = true) {
    module("bulk") {
      contentRoot("contentRoot") {
        sourceRoot("many") {}
      }
    }
  }

  private val project by projectFixture
  private val manyRoot by projectFixture.fileOrDirInProjectFixture("bulk/contentRoot/many")

  // Strong references. The trackers, the provider maps and the pointers are reachable from here only.
  private val manyFiles = mutableListOf<VirtualFile>()
  private val manyPsiFiles = mutableListOf<PsiFile>()
  private val manyFilePointers = mutableListOf<SmartPsiElementPointer<PsiFile>>()
  private val rangePointers = mutableListOf<SmartPsiFileRange>()

  /**
   * This benchmark calls the real `possiblyInvalidatePhysicalPsi`, because a direct bump of the
   * counter skips the code under test. So the number holds three things: the walk of the view
   * provider cache in `MultiverseFileViewProviderCache.markPossiblyInvalidated`, the reanimation of
   * every file provider map on the first touch, and one tracker scan per file.
   *
   * It is not the isolated number for the scan.
   * [SmartPointerRevalidationPerformanceTest.benchmarkLazyRevalidationUnderRepeatedBumps] is.
   *
   * The bump and the resolution stay in separate actions. The bump needs the write lock, and the
   * reanimation of a provider map behaves differently inside a versioning transaction.
   */
  @Test
  fun benchmarkBulkInvalidationManyFiles() {
    timeoutRunBlocking(timeout = BENCHMARK_PREPARE_TIMEOUT) { prepareManyFiles() }

    Benchmark.newBenchmark("smart pointer bulk invalidation across many files") {
      repeat(BULK_ROUNDS) {
        project.bulkInvalidatePsi()
        runReadActionBlocking {
          for (i in manyFilePointers.indices) {
            val restored = manyFilePointers[i].element
            assertNotNull(restored, "The file pointer $i died")
            assertEquals(manyFiles[i], restored!!.virtualFile, "The file pointer $i moved to another file")
          }
        }
      }
    }
      .setup { runReadActionBlocking { manyFilePointers.forEach { it.element } } }
      .attempts(BENCHMARK_ATTEMPTS)
      .start("${javaClass.name}.benchmarkBulkInvalidationManyFiles")

    assertTrue(manyPsiFiles.all { it.isValid }, "Every file must still be valid")
  }

  // --- preparation ---

  private suspend fun prepareManyFiles() {
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    manyFiles += edtWriteAction {
      (0 until FILE_COUNT).map { i ->
        manyRoot.createChildData(this, "file$i.txt").also { VfsUtil.saveText(it, blankText(RANGES_PER_FILE + 1)) }
      }
    }
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    readAction {
      val manager = SmartPointerManagerEx.getInstanceEx(project)
      for (vFile in manyFiles) {
        // The PSI file keeps the file provider map in the cache. A collected map would hide the work.
        val psiFile = PsiManager.getInstance(project).findFile(vFile)!!
        manyPsiFiles += psiFile
        manyFilePointers += SmartPointerManager.createPointer(psiFile)
        repeat(RANGES_PER_FILE) { j ->
          rangePointers += manager.createSmartPsiFileRangePointer(psiFile, TextRange(j, j + 1))
        }
      }
      manyFilePointers.forEach { it.element }
    }
    assertEquals(FILE_COUNT, manyPsiFiles.size)
    assertEquals(FILE_COUNT * RANGES_PER_FILE, rangePointers.size)
  }

  private companion object {
    const val FILE_COUNT: Int = 1_000
    const val RANGES_PER_FILE: Int = 20
    const val BULK_ROUNDS: Int = 30
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.CodeInsightContextManager
import com.intellij.codeInsight.multiverse.ProjectModelContextBridge
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.module.ModuleManager
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
import com.intellij.psi.impl.smartPointers.SmartPointerTracker
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.SkipSlowTestLocally
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.StressTestApplication
import com.intellij.testFramework.junit5.fixture.fileOrDirInProjectFixture
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Benchmarks for the creation of smart pointers: a new tracker per file, many pointers in one file,
 * and many pointers in one file with many code insight contexts.
 *
 * Each body creates its pointers and then removes them with [SmartPointerManager.removePointer], so
 * every attempt does the same work. See `smartPointerBenchmarks.kt` for the rules a body must follow.
 */
@SkipSlowTestLocally
@StressTestApplication
@PerformanceUnitTest
internal class SmartPointerCreationPerformanceTest {
  private val projectFixture = multiverseProjectFixture(withSharedSourceEnabled = true, openAfterCreation = true) {
    module(OWNER_MODULE) {
      contentRoot("ownerRoot") {
        sourceRoot("many") {}
        sourceRoot("shared", SHARED_ROOT_ID) {
          file("shared.txt", blankText(RANGE_COUNT + 1))
        }
        sourceRoot("single") {
          file("single.txt", blankText(RANGE_COUNT + 1))
        }
      }
    }
    for (i in 1 until CONTEXT_COUNT) {
      module(sharingModule(i)) {
        sharedSourceRoot(SHARED_ROOT_ID)
      }
    }
  }

  private val project by projectFixture
  private val manyRoot by projectFixture.fileOrDirInProjectFixture("$OWNER_MODULE/ownerRoot/many")
  private val sharedFile by projectFixture.fileOrDirInProjectFixture("$OWNER_MODULE/ownerRoot/shared/shared.txt")
  private val singleFile by projectFixture.fileOrDirInProjectFixture("$OWNER_MODULE/ownerRoot/single/single.txt")

  // Strong references. The PSI files, the trackers and the pointers are reachable from here only.
  private val manyPsiFiles = ArrayList<PsiFile>()
  private val psiFilePerContext = ArrayList<PsiFile>()
  private val anchorPointers = ArrayList<SmartPsiElementPointer<PsiFile>>()
  private val filePointers = ArrayList<SmartPsiElementPointer<PsiFile>>()
  private val rangePointers = ArrayList<SmartPsiFileRange>()

  /**
   * The Project View shape: one file pointer per file, on files without a tracker.
   *
   * The setup drops the trackers of the previous attempt, so each attempt measures the tracker,
   * the pointer and the reference of every file.
   */
  @Test
  fun benchmarkFilePointerPerFile() {
    timeoutRunBlocking(timeout = BENCHMARK_PREPARE_TIMEOUT) { prepareManyFiles() }
    val pointerManager = SmartPointerManager.getInstance(project)

    Benchmark.newBenchmark("smart pointer creation, one file pointer per file") {
      runReadActionBlocking {
        for (psiFile in manyPsiFiles) {
          filePointers += SmartPointerManager.createPointer(psiFile)
        }
      }
      assertEquals(FILE_COUNT, filePointers.size)
      filePointers.forEach { pointerManager.removePointer(it) }
    }
      .setup { dropFilePointersAndTrackers() }
      .attempts(BENCHMARK_ATTEMPTS)
      .start("${javaClass.name}.benchmarkFilePointerPerFile")
  }

  /**
   * Many range pointers in one file with one context. Each pointer interns its file holder, and the
   * reference list of the tracker grows from empty to [RANGE_COUNT] slots.
   */
  @Test
  fun benchmarkRangePointersInOneFile() {
    timeoutRunBlocking(timeout = BENCHMARK_PREPARE_TIMEOUT) { prepareAnchors(listOf(singleFile)) }
    val psiFile = psiFilePerContext.single()

    Benchmark.newBenchmark("smart pointer creation, range pointers in one file") {
      createAndRemoveRangePointers { psiFile }
    }
      .attempts(BENCHMARK_ATTEMPTS)
      .start("${javaClass.name}.benchmarkRangePointersInOneFile")
  }

  /**
   * Many range pointers in one file with [CONTEXT_COUNT] contexts. The pointers cycle through the
   * contexts, so the holder lookup sees every context. A file has few contexts, so this is the
   * stress end, not the common case.
   */
  @Test
  fun benchmarkPointersInManyContexts() {
    timeoutRunBlocking(timeout = BENCHMARK_PREPARE_TIMEOUT) { prepareAnchors(List(CONTEXT_COUNT) { sharedFile }) }

    Benchmark.newBenchmark("smart pointer creation, range pointers in $CONTEXT_COUNT contexts") {
      createAndRemoveRangePointers { i -> psiFilePerContext[i % CONTEXT_COUNT] }
    }
      .attempts(BENCHMARK_ATTEMPTS)
      .start("${javaClass.name}.benchmarkPointersInManyContexts")
  }

  private fun createAndRemoveRangePointers(psiFileOf: (Int) -> PsiFile) {
    val manager = SmartPointerManagerEx.getInstanceEx(project)
    runReadActionBlocking {
      for (i in 0 until RANGE_COUNT) {
        rangePointers += manager.createSmartPsiFileRangePointer(psiFileOf(i), TextRange(i, i + 1))
      }
    }
    assertEquals(RANGE_COUNT, project.smartPointersNumber(psiFilePerContext[0]), "The tracker must hold every range pointer")
    rangePointers.forEach { manager.removePointer(it) }
    rangePointers.clear()
    assertEquals(0, project.smartPointersNumber(psiFilePerContext[0]), "The tracker must hold no range pointer")
  }

  private fun dropFilePointersAndTrackers() {
    filePointers.clear()
    System.gc()
    SmartPointerTracker.processQueue()
  }

  // --- preparation ---

  private suspend fun prepareManyFiles() {
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    val files = edtWriteAction {
      (0 until FILE_COUNT).map { i ->
        manyRoot.createChildData(this, "file$i.txt").also { VfsUtil.saveText(it, " ") }
      }
    }
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    readAction {
      val psiManager = PsiManager.getInstance(project)
      files.mapTo(manyPsiFiles) { psiManager.findFile(it)!! }
    }
    filePointers.ensureCapacity(FILE_COUNT)
    assertEquals(FILE_COUNT, manyPsiFiles.size)
  }

  /**
   * Finds the PSI file of each entry of [files] in its own context and gives it a file pointer. The
   * file pointer keeps the tracker alive between the attempts.
   */
  private suspend fun prepareAnchors(files: List<VirtualFile>) {
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    readAction {
      val contexts = contextsOf(files.size)
      val psiManager = PsiManager.getInstance(project)
      for (i in files.indices) {
        val psiFile = psiManager.findFile(files[i], contexts[i])!!
        psiFilePerContext += psiFile
        anchorPointers += SmartPointerManager.createPointer(psiFile)
      }
      assertEquals(files.size, psiFilePerContext.distinct().size, "Each context must give its own PSI file")
    }
    rangePointers.ensureCapacity(RANGE_COUNT)
  }

  /** The contexts of the owner module and of the first `count - 1` sharing modules. */
  private fun contextsOf(count: Int): List<CodeInsightContext> {
    val bridge = ProjectModelContextBridge.getInstance(project)
    val moduleManager = ModuleManager.getInstance(project)
    val modules = listOf(OWNER_MODULE) + (1 until count).map { sharingModule(it) }
    val contexts = modules.map { bridge.getContext(moduleManager.findModuleByName(it)!!)!! }
    if (count > 1) {
      val actual = CodeInsightContextManager.getInstance(project).getCodeInsightContexts(sharedFile)
      assertEquals(CONTEXT_COUNT, actual.size, "The shared file must belong to every module")
    }
    return contexts
  }

  private companion object {
    const val OWNER_MODULE: String = "owner"
    const val SHARED_ROOT_ID: String = "sharedRoot"
    const val FILE_COUNT: Int = 5_000
    const val RANGE_COUNT: Int = 50_000
    const val CONTEXT_COUNT: Int = 50

    fun sharingModule(index: Int): String = "sharing$index"
  }
}

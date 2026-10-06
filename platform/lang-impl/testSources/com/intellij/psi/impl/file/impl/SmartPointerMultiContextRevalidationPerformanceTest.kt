// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.CodeInsightContextManager
import com.intellij.codeInsight.multiverse.ProjectModelContextBridge
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.module.ModuleManager
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Benchmarks for the revalidation of one file that belongs to many code insight contexts.
 *
 * One tracker serves all contexts of a file, and the context lives on the file holder of each
 * element info. These two benchmarks cover the fallback branch of
 * [com.intellij.psi.impl.smartPointers.SmartPointerTracker.revalidate]: one with every context
 * alive, and one after a move that kills all contexts but one.
 *
 * See `smartPointerBenchmarks.kt` for the rules a body must follow, and for the other two
 * benchmark classes.
 */
@SkipSlowTestLocally
@StressTestApplication
@PerformanceUnitTest
internal class SmartPointerMultiContextRevalidationPerformanceTest {
  private val projectFixture = multiverseProjectFixture(withSharedSourceEnabled = true, openAfterCreation = true) {
    module(OWNER_MODULE) {
      contentRoot("ownerRoot") {
        sourceRoot("shared", SHARED_ROOT_ID) {
          file("live.txt", blankText(RANGE_COUNT + 1))
          file("dead.txt", blankText(RANGE_COUNT + 1))
        }
      }
    }
    // The first sharing module also owns the move target, so one context survives the move.
    module(sharingModule(0)) {
      sharedSourceRoot(SHARED_ROOT_ID)
      contentRoot("targetRoot") {
        sourceRoot("target") {}
      }
    }
    for (i in 1 until CONTEXT_COUNT - 1) {
      module(sharingModule(i)) {
        sharedSourceRoot(SHARED_ROOT_ID)
        contentRoot("root$i") {
          sourceRoot("src$i") {}
        }
      }
    }
  }

  private val project by projectFixture
  private val liveFile by projectFixture.fileOrDirInProjectFixture("$OWNER_MODULE/ownerRoot/shared/live.txt")
  private val deadFile by projectFixture.fileOrDirInProjectFixture("$OWNER_MODULE/ownerRoot/shared/dead.txt")
  private val targetRoot by projectFixture.fileOrDirInProjectFixture("${sharingModule(0)}/targetRoot/target")

  // Strong references. The tracker and the pointers are reachable from here only.
  private val contexts = mutableListOf<CodeInsightContext>()
  private val psiFilePerContext = mutableListOf<PsiFile>()
  private val filePointerPerContext = mutableListOf<SmartPsiElementPointer<PsiFile>>()
  private val pointersPerContext = mutableListOf<List<SmartPsiFileRange>>()

  /**
   * The fallback branch with many live contexts and nothing dead.
   *
   * This is the common case. The generation moves, the fallback collects the context of every
   * pointer into a set, asks the context manager for the actual contexts, and finds no dead one.
   * A project pays this on every change of the roots.
   */
  @Test
  fun benchmarkFallbackWithLiveContexts() {
    timeoutRunBlocking(timeout = BENCHMARK_PREPARE_TIMEOUT) { prepareContextPointers(liveFile) }

    Benchmark.newBenchmark("smart pointer revalidation fallback, $CONTEXT_COUNT contexts") {
      runWriteActionAndWait {
        var resolved = 0
        repeat(BUMP_COUNT) { round ->
          project.bumpSmartPointerGeneration()
          if (pointersPerContext[round % CONTEXT_COUNT][0].element != null) resolved++
        }
        assertEquals(BUMP_COUNT, resolved, "Every resolution after a bump must succeed")
      }
    }
      .setup { runReadActionBlocking { pointersPerContext[0][0].element } }
      .attempts(BENCHMARK_ATTEMPTS)
      .start("${javaClass.name}.benchmarkFallbackWithLiveContexts")

    // Each pointer must still belong to its own context. A collapsed holder would resolve to null.
    runReadActionBlocking {
      val contextManager = CodeInsightContextManager.getInstance(project)
      for (i in 0 until CONTEXT_COUNT) {
        val file = filePointerPerContext[i].element
        assertNotNull(file, "The pointer of the context ${contexts[i]} died")
        assertEquals(contexts[i], contextManager.getCodeInsightContext(file!!.viewProvider),
                     "The pointer moved to another context")
      }
    }
  }

  /**
   * The dead-context branch, after a move that kills all contexts but one.
   *
   * This runs with one attempt and no warmup on purpose. The branch gives a dead context a null
   * file holder, and such a pointer never resolves again, so a second attempt would scan a smaller
   * live set and report a smaller number. The result is a single unwarmed sample. Compare it only
   * against itself.
   */
  @Test
  fun benchmarkRevalidationAfterContextDeath() {
    timeoutRunBlocking(timeout = BENCHMARK_PREPARE_TIMEOUT) {
      prepareContextPointers(deadFile)
      // The file goes into a root of the first sharing module only, so only that context survives.
      edtWriteAction { deadFile.move(this, targetRoot) }
      IndexingTestUtil.waitUntilIndexesAreReady(project)
    }

    Benchmark.newBenchmark("smart pointer revalidation after context death") {
      runReadActionBlocking {
        var resolved = 0
        for (pointers in pointersPerContext) {
          for (pointer in pointers) {
            if (pointer.element != null) resolved++
          }
        }
        assertEquals(RANGE_COUNT, resolved,
                     "Exactly one context must survive the move, with all of its pointers")
      }
    }
      .attempts(1)
      .warmupIterations(0)
      .start("${javaClass.name}.benchmarkRevalidationAfterContextDeath")

    runReadActionBlocking {
      val survivor = filePointerPerContext[SURVIVING_CONTEXT].element
      assertNotNull(survivor, "The context of the move target must survive")
      assertEquals(deadFile, survivor!!.virtualFile)
      for (i in 0 until CONTEXT_COUNT) {
        if (i == SURVIVING_CONTEXT) continue
        assertNull(filePointerPerContext[i].element, "The dead context $i must not resolve")
      }
    }
  }

  // --- preparation ---

  private suspend fun prepareContextPointers(vFile: VirtualFile) {
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    readAction {
      val bridge = ProjectModelContextBridge.getInstance(project)
      val moduleManager = ModuleManager.getInstance(project)
      // The context of the move target comes first, so SURVIVING_CONTEXT names it.
      contexts += bridge.getContext(moduleManager.findModuleByName(sharingModule(0))!!)!!
      contexts += bridge.getContext(moduleManager.findModuleByName(OWNER_MODULE)!!)!!
      for (i in 1 until CONTEXT_COUNT - 1) {
        contexts += bridge.getContext(moduleManager.findModuleByName(sharingModule(i))!!)!!
      }
      assertEquals(CONTEXT_COUNT, contexts.distinct().size, "Every module must give its own context")

      val actual = CodeInsightContextManager.getInstance(project).getCodeInsightContexts(vFile)
      assertEquals(CONTEXT_COUNT, actual.size, "The shared file must belong to every module")

      val manager = SmartPointerManagerEx.getInstanceEx(project)
      for (context in contexts) {
        val psiFile = PsiManager.getInstance(project).findFile(vFile, context)!!
        psiFilePerContext += psiFile
        filePointerPerContext += SmartPointerManager.createPointer(psiFile)
        pointersPerContext += (0 until RANGE_COUNT).map { i ->
          manager.createSmartPsiFileRangePointer(psiFile, TextRange(i, i + 1))
        }
      }
      pointersPerContext.forEach { pointers -> pointers.forEach { it.element } }
      assertEquals(CONTEXT_COUNT, psiFilePerContext.distinct().size, "Each context must give its own PSI file")
    }
  }

  private companion object {
    const val OWNER_MODULE: String = "owner"
    const val SHARED_ROOT_ID: String = "sharedRoot"
    const val CONTEXT_COUNT: Int = 8
    const val RANGE_COUNT: Int = 5_000
    const val BUMP_COUNT: Int = 300

    /** The index of the context that holds the module of the move target. */
    const val SURVIVING_CONTEXT: Int = 0

    fun sharingModule(index: Int): String = "sharing$index"
  }
}

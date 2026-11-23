// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.versioning

import com.intellij.concurrency.installThreadContext
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.stubs.StubTree
import com.intellij.psi.util.PsiVersioningService
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.junit5.StressTestApplication
import com.intellij.testFramework.junit5.fixture.editorFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import com.intellij.util.concurrency.ThreadingAssertions
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.ref.Reference
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Measures the green stub accessors of [PsiFileImpl].
 * Each accessor checks [com.intellij.psi.impl.source.tree.mvcc.InternalPsiVersioning.isInsideVersioningButNotLocks]
 * and the read access, so these checks dominate a call when the green stub is already loaded.
 */
@StressTestApplication
internal class VersionedGreenStubPerformanceTest {

  private companion object {
    private const val CALLS_PER_ATTEMPT = 1_000_000

    private val TEXT = """
      class Small {
        void existing() {
        }
      }
    """.trimIndent()

    /** An IDE thread context holds many elements, and the lookup of a key walks all of them. */
    private val INFLATED_CONTEXT: CoroutineContext = (1..8).fold(EmptyCoroutineContext as CoroutineContext) { context, _ ->
      context + PaddingElement(PaddingKey())
    }
  }

  private class PaddingKey : CoroutineContext.Key<PaddingElement>

  private class PaddingElement(override val key: CoroutineContext.Key<*>) : CoroutineContext.Element

  private val _project = projectFixture(openAfterCreation = true)
  private val _module = _project.moduleFixture("src")
  private val _sourceRoot = _module.sourceRootFixture()
  private val _psiFile = _sourceRoot.psiFileFixture("Small.java", TEXT)
  private val _editor = _psiFile.editorFixture()

  private val project by _project
  private val editor by _editor

  /** Keeps the results of the accessors observable, so that the JIT cannot remove the calls. */
  @Volatile
  private var sink: Int = 0

  @BeforeEach
  fun awaitIndexing() {
    IndexingTestUtil.waitUntilIndexesAreReady(project)
  }

  @Suppress("DEPRECATION")
  @Test
  fun `getGreenStubTree in read action`() {
    benchmarkInReadAction("getGreenStubTreeInReadAction") { file -> file.greenStubTree }
  }

  @Test
  fun `withGreenStubOrAst in read action`() {
    benchmarkInReadAction("withGreenStubOrAstInReadAction") { file -> file.withGreenStubOrAst({ it }, { it }) }
  }

  @Test
  fun `withGreenStubTreeOrAst in read action`() {
    benchmarkInReadAction("withGreenStubTreeOrAstInReadAction") { file -> file.withGreenStubTreeOrAst({ it }, { it }) }
  }

  @Suppress("DEPRECATION")
  @Test
  fun `getGreenStubTree in frozen psi version`() {
    benchmarkInFrozenPsiVersion("getGreenStubTreeInFrozenPsiVersion") { file -> file.greenStubTree }
  }

  @Test
  fun `withGreenStubOrAst in frozen psi version`() {
    benchmarkInFrozenPsiVersion("withGreenStubOrAstInFrozenPsiVersion") { file -> file.withGreenStubOrAst({ it }, { it }) }
  }

  @Test
  fun `withGreenStubTreeOrAst in frozen psi version`() {
    benchmarkInFrozenPsiVersion("withGreenStubTreeOrAstInFrozenPsiVersion") { file -> file.withGreenStubTreeOrAst({ it }, { it }) }
  }

  private fun benchmarkInReadAction(name: String, accessor: (PsiFileImpl) -> Any?) {
    benchmark(name) { file ->
      runReadActionBlocking {
        repeatAccessor(file, accessor)
      }
    }
  }

  private fun benchmarkInFrozenPsiVersion(name: String, accessor: (PsiFileImpl) -> Any?) {
    benchmark(name) { file ->
      PsiVersioningService.freezePsiVersion {
        // A frozen version degrades to a plain read action when versioning is switched off, then this benchmark measures the wrong path.
        ThreadingAssertions.assertNoReadAccess()
        repeatAccessor(file, accessor)
      }
    }
  }

  private fun repeatAccessor(file: PsiFileImpl, accessor: (PsiFileImpl) -> Any?) {
    var nonNullResults = 0
    repeat(CALLS_PER_ATTEMPT) {
      if (accessor(file) != null) {
        nonNullResults++
      }
    }
    sink = nonNullResults
  }

  private fun benchmark(name: String, attempt: (PsiFileImpl) -> Unit) {
    val (file, stubTree) = loadStubsAndAst()
    installThreadContext(INFLATED_CONTEXT) {
      Benchmark.newBenchmark(name) {
        attempt(file)
      }.warmupIterations(5)
        .attempts(5)
        .start()
    }
    // `FileTrees` holds the stub tree only softly, so keep it alive until the end of the benchmark.
    Reference.reachabilityFence(stubTree)
  }

  /**
   * Brings the tested file into the state where the stub tree and the AST coexist, so that the green stub is available.
   */
  @Suppress("DEPRECATION")
  private fun loadStubsAndAst(): Pair<PsiFileImpl, StubTree> = runReadActionBlocking {
    val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) as PsiFileImpl
    val stubTree = file.calcStubTree()
    Assertions.assertSame(stubTree, file.greenStubTree, "The freshly built stub tree must be the green stub tree of the file")
    file to stubTree
  }
}

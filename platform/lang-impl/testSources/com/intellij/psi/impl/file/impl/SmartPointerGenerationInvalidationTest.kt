// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.CodeInsightContextManager
import com.intellij.codeInsight.multiverse.CodeInsightContextManagerImpl
import com.intellij.codeInsight.multiverse.CodeInsightContextProvider
import com.intellij.codeInsight.multiverse.CodeInsightContextProvider.Answer
import com.intellij.codeInsight.multiverse.codeInsightContext
import com.intellij.codeInsight.multiverse.defaultContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.multiverseProjectFixture
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiFileRange
import com.intellij.psi.impl.DebugUtil
import com.intellij.psi.impl.PsiManagerImpl
import com.intellij.psi.impl.smartPointers.SmartPointerManagerEx
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.virtualFileFixture
import com.intellij.testFramework.utils.vfs.createFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.io.path.Path

/**
 * Focused tests for the smart-pointer side of generation-based invalidation.
 *
 * Smart pointers go through `SmartPointerManagerImpl.possiblyInvalidate` which only
 * increments the generation counter when shared-source support is enabled. So all of
 * these tests use a multiverse project (`withSharedSourceEnabled = true`).
 */
@TestApplication
internal class SmartPointerGenerationInvalidationTest {
  private val projectFixture = multiverseProjectFixture(withSharedSourceEnabled = true) {}
  private val sourceRoot = projectFixture.moduleFixture("SmartPointerGenerationInvalidationTest").sourceRootFixture()
  private val javaFile by sourceRoot.virtualFileFixture("A.java", "class A {}")
  private val twoContextJavaFile by sourceRoot.virtualFileFixture("B.java", "class B {}")
  private val replacedContextJavaFile by sourceRoot.virtualFileFixture("C.java", "class C {}")
  private val droppedContextJavaFile by sourceRoot.virtualFileFixture("D.java", "class D {}")
  private val remappedContextJavaFile by sourceRoot.virtualFileFixture("E.java", "class E {}")
  private val copiedTextFile by sourceRoot.virtualFileFixture("F.txt", "text")
  private val disposable by disposableFixture()

  /**
   * The generation counter exposed by [SmartPointerManagerEx.getPossiblyInvalidationModCounter]
   * increments by exactly 1 per `possiblyInvalidatePhysicalPsi()` call, regardless of how many
   * smart pointers exist.
   */
  @Test
  fun `generation increments by exactly one per bulk invalidation regardless of pointer count`() = timeoutRunBlocking {
    val (_, psiFile) = createPsiFile("sp_gen_count.txt")
    val pointers = readAction {
      List(10) { SmartPointerManager.createPointer(psiFile) }
    }
    assertEquals(10, pointers.size)

    val genBefore = smartPointerManager.possiblyInvalidationModCounter.modificationCount
    triggerBulkInvalidation()
    val genAfter = smartPointerManager.possiblyInvalidationModCounter.modificationCount

    assertEquals(genBefore + 1, genAfter, "Generation should increment by 1, not by the number of cached smart pointers")
  }

  /**
   * A smart pointer that has not seen any invalidation is not stale and resolves directly.
   */
  @Test
  fun `freshly created pointer is not stale`() = timeoutRunBlocking {
    val (_, psiFile) = createPsiFile("sp_fresh.txt")
    val pointer = readAction { SmartPointerManager.createPointer(psiFile) }

    val resolved = readAction { pointer.element }
    assertNotNull(resolved, "Fresh pointer should resolve")
    assertSame(psiFile, resolved, "Fresh pointer should resolve to the same PSI file")
  }

  /**
   * A pointer created AFTER a bulk invalidation has its tracker pre-initialised to the
   * current generation, so it resolves without forcing a revalidation cycle.
   */
  @Test
  fun `pointer created after invalidation is initialised at the current generation`() = timeoutRunBlocking {
    triggerBulkInvalidation()
    val genAtCreation = smartPointerManager.possiblyInvalidationModCounter.modificationCount
    assertTrue(genAtCreation > 0L, "Expected at least one prior invalidation")

    val (_, psiFile) = createPsiFile("sp_created_after_inv.txt")
    val pointer = readAction { SmartPointerManager.createPointer(psiFile) }

    val resolved = readAction { pointer.element }
    assertNotNull(resolved, "Pointer created after invalidation must still resolve")
    assertSame(psiFile, resolved)

    // Without any new invalidations the pointer must resolve consistently.
    val resolvedAgain = readAction { pointer.element }
    assertSame(resolved, resolvedAgain)
  }

  /**
   * After a bulk invalidation, an existing pointer is observably affected (its tracker's
   * generation lags the global one). Resolving it triggers a revalidation; after that, the
   * pointer continues to resolve to the same element.
   */
  @Test
  fun `pointer survives a single bulk invalidation`() = timeoutRunBlocking {
    val (_, psiFile) = createPsiFile("sp_survive_one.txt")
    val pointer = readAction { SmartPointerManager.createPointer(psiFile) }
    val first = readAction { pointer.element }
    assertNotNull(first)

    triggerBulkInvalidation()

    val afterInv = readAction { pointer.element }
    assertNotNull(afterInv, "Pointer should resolve after a bulk invalidation")
    assertSame(first, afterInv, "Same PSI element should be returned")
  }

  /**
   * Pointers must survive N successive bulk invalidations. After each invalidation a single
   * `pointer.element` call must successfully revalidate the tracker.
   */
  @Test
  fun `pointer survives many sequential bulk invalidations`() = timeoutRunBlocking {
    val (_, psiFile) = createPsiFile("sp_survive_many.txt")
    val pointer = readAction { SmartPointerManager.createPointer(psiFile) }
    val originalElement = readAction { pointer.element!! }

    repeat(7) { round ->
      triggerBulkInvalidation()
      val resolved = readAction { pointer.element }
      assertNotNull(resolved, "Pointer should resolve after invalidation #${round + 1}")
      assertSame(originalElement, resolved, "Pointer should resolve to the same PSI file each round")
    }
  }

  /**
   * After a pointer's first resolution following an invalidation (which performs revalidation
   * via `SmartPointerTracker.revalidate`), the second resolution must NOT trigger another
   * revalidation: the tracker's `validatedAtGeneration` is now caught up with the global counter.
   *
   * We verify this observationally by ensuring the global generation didn't change between
   * the two resolutions and that the second resolution returns the same element instantly.
   */
  @Test
  fun `tracker generation catches up after first resolution following invalidation`() = timeoutRunBlocking {
    val (_, psiFile) = createPsiFile("sp_revalidate.txt")
    val pointer = readAction { SmartPointerManager.createPointer(psiFile) }

    triggerBulkInvalidation()
    val genBefore = smartPointerManager.possiblyInvalidationModCounter.modificationCount

    val firstResolve = readAction { pointer.element }
    val genAfterFirst = smartPointerManager.possiblyInvalidationModCounter.modificationCount
    val secondResolve = readAction { pointer.element }
    val genAfterSecond = smartPointerManager.possiblyInvalidationModCounter.modificationCount

    assertEquals(genBefore, genAfterFirst, "Resolving a pointer must not increment the global generation")
    assertEquals(genBefore, genAfterSecond, "Resolving a pointer must not increment the global generation")
    assertNotNull(firstResolve)
    assertSame(firstResolve, secondResolve)
  }

  /**
   * Smart pointers for different files share the same global generation counter — one bulk
   * invalidation marks all of them stale, and each one can recover independently.
   */
  @Test
  fun `bulk invalidation affects pointers across multiple files`() = timeoutRunBlocking {
    val (_, fileA) = createPsiFile("sp_multi_a.txt")
    val (_, fileB) = createPsiFile("sp_multi_b.txt")
    val (_, fileC) = createPsiFile("sp_multi_c.txt")

    val pointers = readAction {
      listOf(
        SmartPointerManager.createPointer(fileA),
        SmartPointerManager.createPointer(fileB),
        SmartPointerManager.createPointer(fileC),
      )
    }
    pointers.forEach { readAction { it.element!! } }  // ensure trackers exist

    val genBefore = smartPointerManager.possiblyInvalidationModCounter.modificationCount
    triggerBulkInvalidation()
    val genAfter = smartPointerManager.possiblyInvalidationModCounter.modificationCount
    assertEquals(genBefore + 1, genAfter)

    // Every pointer must resolve correctly to its file
    val resolved = readAction { pointers.map { it.element } }
    assertSame(fileA, resolved[0])
    assertSame(fileB, resolved[1])
    assertSame(fileC, resolved[2])
  }

  /**
   * Multiple pointers to the same PSI file share a single underlying tracker; all of them
   * must resolve correctly after a bulk invalidation.
   */
  @Test
  fun `multiple pointers to the same file all resolve after invalidation`() = timeoutRunBlocking {
    val (_, psiFile) = createPsiFile("sp_shared_tracker.txt")
    val pointers = readAction { List(5) { SmartPointerManager.createPointer(psiFile) } }
    pointers.forEach { readAction { it.element!! } }

    triggerBulkInvalidation()

    val resolved = readAction { pointers.map { it.element } }
    assertTrue(resolved.all { it === psiFile }, "All pointers should resolve to the same PSI file")
  }

  /**
   * `SmartPsiFileRange` pointers also survive bulk invalidation: range info should still be
   * present and accurate after revalidation.
   */
  @Test
  fun `range pointer survives bulk invalidation`() = timeoutRunBlocking {
    val (_, psiFile) = createPsiFile("sp_range.txt")
    val range = TextRange(0, 0)
    val rangePointer = readAction {
      SmartPointerManager.getInstance(project).createSmartPsiFileRangePointer(psiFile, range)
    }
    val rangeBefore = readAction { rangePointer.range }
    assertNotNull(rangeBefore)

    triggerBulkInvalidation()

    val rangeAfter = readAction { rangePointer.range }
    assertNotNull(rangeAfter, "Range pointer should still produce a range after bulk invalidation")
    assertEquals(rangeBefore, rangeAfter, "Range value should match across invalidation")
  }

  @Test
  fun `stub-based pointer resolves into a reassigned view provider after its context is replaced`() = timeoutRunBlocking {
    IndexingTestUtil.waitUntilIndexesAreReady(project)

    val oldContext = readAction { CodeInsightContextManager.getInstance(project).getCodeInsightContexts(javaFile).single() }

    val (psiFile, pointer) = readAction {
      val psiFile = PsiManager.getInstance(project).findFile(javaFile, oldContext) as PsiFileImpl
      val psiClass = psiFile.stubTree!!.plainList.map { it.psi }.single { it is PsiNamedElement && it.name == "A" }
      assertNull(psiFile.treeElement, "The pointer must be stub-based")
      psiFile to SmartPointerManager.createPointer(psiClass)
    }

    edtWriteAction {
      CodeInsightContextManager.getInstance(project)
        .registerTestOnlyCodeInsightContextProvider(TestFileContextProvider(javaFile, listOf(TestContext("new"))), disposable)
      DebugUtil.performPsiModification<Throwable>("") {
        fileManager.possiblyInvalidatePhysicalPsi()
      }
    }

    readAction {
      val element = pointer.element
      assertNotNull(element, "Pointer should resolve after its context is replaced")
      assertEquals(listOf(psiFile.viewProvider), fileManager.findCachedViewProviders(javaFile),
                   "Pointer must not create a view provider for the old context")
      assertSame(psiFile, element!!.containingFile)
    }
  }

  /**
   * One [com.intellij.psi.impl.smartPointers.SmartPointerTracker] serves all contexts of a file.
   * Two dead contexts can collapse into one new context. Each context has its own view provider,
   * so only one of them can continue into the new context. The pointers of that context must
   * survive and must resolve into the new context, and the pointers of the other context must not.
   *
   * The two pointers also differ in kind. The stub-based pointer has no range, so `getSortedInfos()`
   * left it out and `revalidate` never saw its context. `getContextAwareInfos()` reports it.
   *
   * A pointer can answer from its own soft element cache for a while, so a non-null result is not
   * proof of a live context. The test checks the context of the restored file instead.
   */
  @Test
  fun `pointers of one of two collapsed contexts survive`() = timeoutRunBlocking {
    val contextManager = CodeInsightContextManager.getInstance(project)
    val contextA = TestContext("A")
    val contextB = TestContext("B")

    val twoContextDisposable = Disposer.newDisposable(disposable, "B.java has the contexts A and B")
    edtWriteAction {
      contextManager.registerTestOnlyCodeInsightContextProvider(
        TestFileContextProvider(twoContextJavaFile, listOf(contextA, contextB)), twoContextDisposable)
    }
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    assertEquals(listOf(contextA, contextB), readAction { contextManager.getCodeInsightContexts(twoContextJavaFile) })

    // A stub-based pointer in context B. Its element info has no range.
    val stubPointer = readAction {
      val psiFile = PsiManager.getInstance(project).findFile(twoContextJavaFile, contextB) as PsiFileImpl
      val psiClass = psiFile.stubTree!!.plainList.map { it.psi }.single { it is PsiNamedElement && it.name == "B" }
      assertNull(psiFile.treeElement, "The pointer must be stub-based")
      SmartPointerManager.createPointer(psiClass)
    }

    // A range-based pointer in context A. Both pointers share one tracker, because the file is the same.
    val rangePointer = readAction {
      val psiFile = PsiManager.getInstance(project).findFile(twoContextJavaFile, contextA)!!
      SmartPointerManager.getInstance(project).createSmartPsiFileRangePointer(psiFile, TextRange(0, 1))
    }
    assertNotNull(readAction { rangePointer.element }, "The range pointer must resolve before the context change")

    // Both contexts die, and one new context replaces them.
    edtWriteAction {
      Disposer.dispose(twoContextDisposable)
      contextManager.registerTestOnlyCodeInsightContextProvider(
        TestFileContextProvider(twoContextJavaFile, listOf(TestContext("new"))), disposable)
      DebugUtil.performPsiModification<Throwable>("") {
        fileManager.possiblyInvalidatePhysicalPsi()
      }
    }

    // Context A and context B cannot both continue into the new context, so exactly one set survives,
    // and it must resolve into the new context. A plain non-null check is not enough here: both
    // `element` and `containingFile` fall back to a soft element cache or to the document, and
    // neither fallback knows about a context.
    readAction {
      val newContext = contextManager.getCodeInsightContexts(twoContextJavaFile).single()
      val files = listOfNotNull(rangePointer.containingFile, stubPointer.containingFile)
      assertEquals(1, files.size,
                   "Exactly one context must survive. Range pointer: ${rangePointer.containingFile}, " +
                   "stub-based pointer: ${stubPointer.containingFile}")
      assertEquals(newContext, contextManager.getCodeInsightContext(files.single().viewProvider),
                   "The surviving pointers must resolve into the new context")
    }
  }

  /**
   * The tracker caches the contexts of its pointers, so a generation bump that changes no context
   * skips the scan over the pointers (IJPL-258041). The cache must not hide a later context change.
   *
   * The first revalidation fills the cache, the second one is answered by it. The context change
   * then goes through the fallback path: the pointer resolves in the same write action as the change,
   * before anything (indexing, for one) reassigns the view provider, so no context mapping is queued.
   *
   * A range pointer answers [com.intellij.psi.SmartPsiFileRange.getContainingFile] from its file
   * holder only, so the context of the result is the context of the holder.
   */
  @Test
  fun `cached contexts do not hide a replaced context`() = timeoutRunBlocking {
    val pointer = createRangePointer(replacedContextJavaFile, context = null)
    val oldContext = readAction { contextOf(pointer) }
    assertNotNull(oldContext)

    repeat(2) { round ->
      triggerBulkInvalidation()
      assertEquals(oldContext, readAction { contextOf(pointer) }, "No context changed in round ${round + 1}")
    }

    val newContext = TestContext("new")
    edtWriteAction {
      CodeInsightContextManager.getInstance(project)
        .registerTestOnlyCodeInsightContextProvider(TestFileContextProvider(replacedContextJavaFile, listOf(newContext)), disposable)
      DebugUtil.performPsiModification<Throwable>("") {
        fileManager.possiblyInvalidatePhysicalPsi()
      }
      assertEquals(newContext, contextOf(pointer), "The pointer must follow the file into the new context")
    }
  }

  /**
   * A pointer created after the cache is filled adds its context to the cache. Without that, the
   * cache would claim that the context is unused, and its death would not reach the pointer.
   */
  @Test
  fun `a context of a pointer created after the cache is filled is dropped when it dies`() = timeoutRunBlocking {
    val contextA = TestContext("A")
    val contextB = TestContext("B")
    val twoContexts = Disposer.newDisposable(disposable, "D.java has the contexts A and B")
    replaceContexts(droppedContextJavaFile, listOf(contextA, contextB), twoContexts, invalidate = false)

    val pointerA = createRangePointer(droppedContextJavaFile, contextA)
    triggerBulkInvalidation()
    assertEquals(contextA, readAction { contextOf(pointerA) }, "The first revalidation fills the cache")

    val pointerB = createRangePointer(droppedContextJavaFile, contextB)
    triggerBulkInvalidation()
    assertEquals(contextB, readAction { contextOf(pointerB) }, "No context changed, so the cache answers")

    Disposer.dispose(twoContexts)
    replaceContexts(droppedContextJavaFile, listOf(contextA), disposable)

    readAction {
      assertEquals(contextA, contextOf(pointerA), "The live context must stay")
      assertNull(pointerB.containingFile, "The pointer of the dead context must not resolve")
    }
  }

  /**
   * The same as `cached contexts do not hide a replaced context`, but the view provider is reassigned
   * before the pointer resolves. The reassignment queues a context mapping, and the tracker must
   * apply it even though its cache is filled. Indexing after the change can already do the reassignment;
   * the explicit `findFile` makes sure it happens.
   */
  @Test
  fun `cached contexts do not hide a queued context mapping`() = timeoutRunBlocking {
    val pointer = createRangePointer(remappedContextJavaFile, context = null)
    val oldContext = readAction { contextOf(pointer) }
    assertNotNull(oldContext)

    triggerBulkInvalidation()
    assertEquals(oldContext, readAction { contextOf(pointer) }, "The first revalidation fills the cache")

    val newContext = TestContext("new")
    replaceContexts(remappedContextJavaFile, listOf(newContext), disposable)

    readAction {
      // Reassigns the view provider of the old context and queues the mapping.
      val file = PsiManager.getInstance(project).findFile(remappedContextJavaFile)!!
      assertEquals(newContext, CodeInsightContextManager.getInstance(project).getCodeInsightContext(file.viewProvider))
      assertEquals(newContext, contextOf(pointer), "The pointer must follow the mapping into the new context")
      assertEquals(listOf(file.viewProvider), fileManager.findCachedViewProviders(remappedContextJavaFile),
                   "Pointer must not create a view provider for the old context")
    }
  }

  /**
   * Completion copies a file and gives the copy the context of the original, as `CompletionInitializationUtil` does.
   * The context manager reports only the default context for the light virtual file of the copy.
   *
   * The first pointer fills the context cache of the tracker. The second pointer has the context of the original,
   * which the cache does not know. No generation bump happens after the second pointer, so it must resolve.
   */
  @Test
  fun `a new pointer into a completion file copy resolves without a generation bump`() = timeoutRunBlocking {
    val contextManager = CodeInsightContextManager.getInstance(project) as CodeInsightContextManagerImpl
    val copy = readAction {
      val original = PsiManager.getInstance(project).findFile(copiedTextFile)!!
      val copy = original.copy() as PsiFile
      contextManager.setCodeInsightContext(copy.viewProvider, contextManager.getCodeInsightContext(original.viewProvider))
      assertNotNull(copy.viewProvider.document, "A tracked pointer needs a document")
      copy
    }
    readAction {
      assertNotEquals(defaultContext(), copy.codeInsightContext, "The copy must keep the context of the original")
      assertEquals(listOf(defaultContext()), contextManager.getCodeInsightContexts(copy.viewProvider.virtualFile))
    }

    val first = readAction { SmartPointerManager.getInstance(project).createSmartPsiFileRangePointer(copy, TextRange(0, 1)) }
    triggerBulkInvalidation()
    readAction { first.element }

    val second = readAction { SmartPointerManager.getInstance(project).createSmartPsiFileRangePointer(copy, TextRange(1, 2)) }
    assertSame(copy, readAction { second.element }, "The new pointer must resolve to the copy")
  }

  /**
   * Completion sets the context of the original on the copy and on its light file, as `CompletionInitializationUtil` does.
   * So the context of the copy stays actual, and a pointer made after the first invalidation resolves after the next one.
   */
  @Test
  fun `a new pointer into a completion file copy resolves after repeated invalidations`() = timeoutRunBlocking {
    val copy = createCompletionCopy()
    readAction {
      assertNotEquals(defaultContext(), copy.codeInsightContext, "The copy must keep the context of the original")
      assertEquals(listOf(copy.codeInsightContext), contextManager.getCodeInsightContexts(copy.viewProvider.virtualFile),
                   "The light file of the copy must report the context of the copy")
    }

    val first = readAction { SmartPointerManager.getInstance(project).createSmartPsiFileRangePointer(copy, TextRange(0, 1)) }
    triggerBulkInvalidation()
    assertSame(copy, readAction { first.element }, "The first pointer must resolve after the initial invalidation")

    val second = readAction { SmartPointerManager.getInstance(project).createSmartPsiFileRangePointer(copy, TextRange(1, 2)) }
    triggerBulkInvalidation()
    assertSame(copy, readAction { second.element }, "The new pointer must resolve after the next invalidation")
  }

  /**
   * A provider owns the light file of the copy, so the file does not report the context of the copy.
   * The first invalidation moves the context of the copy to the single actual context.
   * The context then becomes actual again. When it dies for the second time, among two actual contexts, its new pointer detaches.
   * The tracker keeps no earlier move, so the pointer does not move to the old target.
   */
  @Test
  fun `a context that becomes actual again does not move to its old target`() = timeoutRunBlocking {
    val contextManager = CodeInsightContextManager.getInstance(project) as CodeInsightContextManagerImpl
    val sourceContext = TestContext("source")
    val actualContext = TestContext("actual")
    val otherContext = TestContext("other")
    val copy = readAction {
      val original = PsiManager.getInstance(project).findFile(copiedTextFile)!!
      val copy = original.copy() as PsiFile
      contextManager.setCodeInsightContext(copy.viewProvider, sourceContext)
      assertNotNull(copy.viewProvider.document, "A tracked pointer needs a document")
      copy
    }
    val copyFile = copy.viewProvider.virtualFile
    val provider = TestFileContextProvider(copyFile, listOf(actualContext))
    edtWriteAction { contextManager.registerTestOnlyCodeInsightContextProvider(provider, disposable) }
    assertEquals(listOf(actualContext), readAction { contextManager.getCodeInsightContexts(copyFile) })

    val first = readAction { SmartPointerManager.getInstance(project).createSmartPsiFileRangePointer(copy, TextRange(0, 1)) }
    triggerBulkInvalidation()
    readAction { first.element }

    provider.contexts = listOf(sourceContext, actualContext)
    contextManager.requestInvalidationNow(setOf(copyFile))
    triggerBulkInvalidation()
    readAction { first.element }

    val second = readAction { SmartPointerManager.getInstance(project).createSmartPsiFileRangePointer(copy, TextRange(1, 2)) }
    provider.contexts = listOf(actualContext, otherContext)
    contextManager.requestInvalidationNow(setOf(copyFile))
    triggerBulkInvalidation()
    assertNull(readAction { second.element }, "The pointer must not move to the target of the forgotten record")
  }

  /**
   * A provider owns the light file of the copy, so the file does not report the context of the copy.
   * The first invalidation moves the context of the copy to the single actual context. That target then dies.
   * When the target is actual again, among two actual contexts, a new pointer with the old context detaches.
   * The tracker keeps no earlier move, so the pointer does not move to the old target.
   */
  @Test
  fun `a context does not move to a target that died after the move`() = timeoutRunBlocking {
    val contextManager = CodeInsightContextManager.getInstance(project) as CodeInsightContextManagerImpl
    val sourceContext = TestContext("source")
    val actualContext = TestContext("actual")
    val replacementContext = TestContext("replacement")
    val otherContext = TestContext("other")
    val copy = readAction {
      val original = PsiManager.getInstance(project).findFile(copiedTextFile)!!
      val copy = original.copy() as PsiFile
      contextManager.setCodeInsightContext(copy.viewProvider, sourceContext)
      assertNotNull(copy.viewProvider.document, "A tracked pointer needs a document")
      copy
    }
    val copyFile = copy.viewProvider.virtualFile
    val provider = TestFileContextProvider(copyFile, listOf(actualContext))
    edtWriteAction { contextManager.registerTestOnlyCodeInsightContextProvider(provider, disposable) }
    assertEquals(listOf(actualContext), readAction { contextManager.getCodeInsightContexts(copyFile) })

    val first = readAction { SmartPointerManager.getInstance(project).createSmartPsiFileRangePointer(copy, TextRange(0, 1)) }
    triggerBulkInvalidation()
    readAction { first.element }

    provider.contexts = listOf(replacementContext)
    contextManager.requestInvalidationNow(setOf(copyFile))
    triggerBulkInvalidation()
    readAction { first.element }

    provider.contexts = listOf(actualContext, otherContext)
    contextManager.requestInvalidationNow(setOf(copyFile))
    val second = readAction { SmartPointerManager.getInstance(project).createSmartPsiFileRangePointer(copy, TextRange(1, 2)) }
    triggerBulkInvalidation()
    assertNull(readAction { second.element }, "The pointer must not move to a target that died after the move")
  }

  /**
   * A provider owns the light file of the copy, so the file does not report the context of the copy.
   * The first invalidation moves the context of the copy. Then every live pointer detaches, so the tracker knows no context.
   * The old context becomes actual again. When it dies again, among two actual contexts, its new pointer detaches.
   * The tracker keeps no earlier move, so the pointer does not move to the old target.
   */
  @Test
  fun `a tracker with only detached pointers forgets a context that becomes actual again`() = timeoutRunBlocking {
    val contextManager = CodeInsightContextManager.getInstance(project) as CodeInsightContextManagerImpl
    val sourceContext = TestContext("source")
    val actualContext = TestContext("actual")
    val detachedContext = TestContext("detached")
    val otherContext = TestContext("other")
    val copy = readAction {
      val original = PsiManager.getInstance(project).findFile(copiedTextFile)!!
      val copy = original.copy() as PsiFile
      contextManager.setCodeInsightContext(copy.viewProvider, sourceContext)
      assertNotNull(copy.viewProvider.document, "A tracked pointer needs a document")
      copy
    }
    val copyFile = copy.viewProvider.virtualFile
    val provider = TestFileContextProvider(copyFile, listOf(actualContext))
    edtWriteAction { contextManager.registerTestOnlyCodeInsightContextProvider(provider, disposable) }
    assertEquals(listOf(actualContext), readAction { contextManager.getCodeInsightContexts(copyFile) })

    val first = readAction { SmartPointerManager.getInstance(project).createSmartPsiFileRangePointer(copy, TextRange(0, 1)) }
    triggerBulkInvalidation()
    readAction { first.element }
    readAction { SmartPointerManager.getInstance(project).removePointer(first) }

    readAction { contextManager.setCodeInsightContext(copy.viewProvider, detachedContext) }
    val detached = readAction { SmartPointerManager.getInstance(project).createSmartPsiFileRangePointer(copy, TextRange(0, 1)) }
    provider.contexts = listOf(actualContext, otherContext)
    contextManager.requestInvalidationNow(setOf(copyFile))
    triggerBulkInvalidation()
    assertNull(readAction { detached.element }, "The pointer with a dead context and no record must detach")

    provider.contexts = listOf(sourceContext, actualContext, otherContext)
    contextManager.requestInvalidationNow(setOf(copyFile))
    triggerBulkInvalidation()
    readAction { detached.element }

    readAction { contextManager.setCodeInsightContext(copy.viewProvider, sourceContext) }
    provider.contexts = listOf(actualContext, otherContext)
    contextManager.requestInvalidationNow(setOf(copyFile))
    val second = readAction { SmartPointerManager.getInstance(project).createSmartPsiFileRangePointer(copy, TextRange(1, 2)) }
    triggerBulkInvalidation()
    assertNull(readAction { second.element }, "The pointer must not move to the target of the forgotten record")
  }

  /**
   * Completion sets the context of the original on the light file of the copy. Then the original gets another context.
   * The light file keeps the context of the copy, so the pointers into the copy keep their context and resolve.
   */
  @Test
  fun `pointers into a completion file copy resolve after the context of the original changes`() = timeoutRunBlocking {
    val sourceContext = TestContext("source")
    val renamedContext = TestContext("renamed")
    val provider = TestFileContextProvider(copiedTextFile, listOf(sourceContext))
    edtWriteAction { contextManager.registerTestOnlyCodeInsightContextProvider(provider, disposable) }
    val copy = createCompletionCopy()
    val copyFile = copy.viewProvider.virtualFile
    readAction {
      assertEquals(sourceContext, copy.codeInsightContext, "The copy must get the context of the original")
      assertEquals(listOf(sourceContext), contextManager.getCodeInsightContexts(copyFile))
    }

    val first = readAction { SmartPointerManager.getInstance(project).createSmartPsiFileRangePointer(copy, TextRange(0, 1)) }
    triggerBulkInvalidation()
    assertSame(copy, readAction { first.element }, "The first pointer must resolve after the first invalidation")

    provider.contexts = listOf(renamedContext)
    contextManager.requestInvalidationNow(setOf(copiedTextFile))
    val second = readAction { SmartPointerManager.getInstance(project).createSmartPsiFileRangePointer(copy, TextRange(1, 2)) }
    triggerBulkInvalidation()
    readAction {
      assertEquals(listOf(sourceContext), contextManager.getCodeInsightContexts(copyFile), "The light file must keep the context of the copy")
      assertSame(copy, first.element, "The first pointer must resolve after the context of the original changes")
      assertSame(copy, second.element, "The second pointer must resolve after the context of the original changes")
    }
  }

  /** A provider owns the context type, but not a light file with an explicit context of that type. */
  @Test
  fun `a light file with an explicit context has no owner`() = timeoutRunBlocking {
    val context = TestContext("light")
    val provider = TestFileContextProvider(copiedTextFile, listOf(TestContext("other")))
    edtWriteAction { contextManager.registerTestOnlyCodeInsightContextProvider(provider, disposable) }
    val file = LightVirtualFile("light.txt", PlainTextFileType.INSTANCE, "text")
    readAction {
      assertTrue(provider.isOwnerOf(context), "Precondition: the provider owns the type of the context")
      contextManager.setCodeInsightContextOfLightFile(file, context)
      assertEquals(listOf(context), contextManager.getCodeInsightContexts(file))
      assertNull(contextManager.withOwnerOf(file) { it }, "A light file with an explicit context must have no owner")
    }
  }

  /** A cached answer for a light file must not hide the context that a later call sets on the file. */
  @Test
  fun `the context of a light file replaces a cached answer`() = timeoutRunBlocking {
    val context = TestContext("light")
    val file = LightVirtualFile("light.txt", PlainTextFileType.INSTANCE, "text")
    readAction {
      assertEquals(listOf(defaultContext()), contextManager.getCodeInsightContexts(file), "The answer is cached")
      contextManager.setCodeInsightContextOfLightFile(file, context)
      assertEquals(listOf(context), contextManager.getCodeInsightContexts(file))
      assertEquals(context, contextManager.getPreferredContext(file))
    }
  }

  ///**
  // * `SmartPointerManagerEx.getInstanceEx(project).getPossiblyInvalidatedGeneration()` and
  // * `((PsiManagerImpl)).fileManagerEx.getPossiblyInvalidatedGeneration()` are independent
  // * counters (smart-pointer side vs. cache side) but both must monotonically increase on
  // * each bulk invalidation when shared-source is enabled.
  // */
  //@Test
  //fun `smart pointer and cache generations both advance per bulk invalidation`() = timeoutRunBlocking {
  //  val smartBefore = smartPointerManager().possiblyInvalidatedGeneration
  //  val cacheBefore = fileManager().possiblyInvalidatedGeneration
  //
  //  repeat(3) { triggerBulkInvalidation() }
  //
  //  val smartAfter = smartPointerManager().possiblyInvalidatedGeneration
  //  val cacheAfter = fileManager().possiblyInvalidatedGeneration
  //
  //  assertEquals(smartBefore + 3, smartAfter, "Smart pointer generation should advance by 3")
  //  assertEquals(cacheBefore + 3, cacheAfter, "Cache generation should advance by 3")
  //}

  private val project
    get() = projectFixture.get()

  private val smartPointerManager: SmartPointerManagerEx
    get() = SmartPointerManagerEx.getInstanceEx(project)

  private val fileManager: FileManagerEx
    get() = PsiManagerImpl.getInstanceEx(project).fileManagerEx

  private suspend fun triggerBulkInvalidation() {
    writeAction {
      DebugUtil.performPsiModification<Throwable>("") {
        fileManager.possiblyInvalidatePhysicalPsi()
      }
    }
  }

  /** A range pointer that is not the whole file, so it gets a [com.intellij.psi.impl.smartPointers.SelfElementInfo]. */
  private suspend fun createRangePointer(file: VirtualFile, context: CodeInsightContext?): SmartPsiFileRange = readAction {
    val psiFile = when (context) {
      null -> PsiManager.getInstance(project).findFile(file)!!
      else -> PsiManager.getInstance(project).findFile(file, context)!!
    }
    SmartPointerManager.getInstance(project).createSmartPsiFileRangePointer(psiFile, TextRange(0, 1))
  }

  private fun contextOf(pointer: SmartPsiFileRange): CodeInsightContext? =
    pointer.containingFile?.let { CodeInsightContextManager.getInstance(project).getCodeInsightContext(it.viewProvider) }

  private suspend fun replaceContexts(
    file: VirtualFile,
    contexts: List<CodeInsightContext>,
    parentDisposable: Disposable,
    invalidate: Boolean = true,
  ) {
    edtWriteAction {
      CodeInsightContextManager.getInstance(project)
        .registerTestOnlyCodeInsightContextProvider(TestFileContextProvider(file, contexts), parentDisposable)
      if (invalidate) {
        DebugUtil.performPsiModification<Throwable>("") {
          fileManager.possiblyInvalidatePhysicalPsi()
        }
      }
    }
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    assertEquals(contexts, readAction { CodeInsightContextManager.getInstance(project).getCodeInsightContexts(file) })
  }

  private val contextManager: CodeInsightContextManagerImpl
    get() = CodeInsightContextManager.getInstance(project) as CodeInsightContextManagerImpl

  /** Makes a copy of [copiedTextFile] with the context of the original, as `CompletionInitializationUtil` does. */
  private suspend fun createCompletionCopy(): PsiFile = readAction {
    val original = PsiManager.getInstance(project).findFile(copiedTextFile)!!
    val copy = original.copy() as PsiFile
    contextManager.setCodeInsightContextOfLightFile(copy.viewProvider.virtualFile as LightVirtualFile,
                                                    contextManager.getCodeInsightContext(original.viewProvider))
    assertNotNull(copy.viewProvider.document, "A tracked pointer needs a document")
    copy
  }

  private suspend fun createPsiFile(name: String): Pair<VirtualFile, PsiFile> {
    val root = readAction { VfsUtil.findFile(Path(project.basePath!!), false)!! }
    val vFile = writeAction { root.createFile(name) }
    val psi = readAction { PsiManager.getInstance(project).findFile(vFile)!! }
    return vFile to psi
  }

}

private class TestContext(private val name: String) : CodeInsightContext {
  override fun toString(): String = "TestContext($name)"
}

// Answers [contexts] for [targetFile] and claims no other file.
private class TestFileContextProvider(
  private val targetFile: VirtualFile,
  @Volatile var contexts: List<CodeInsightContext>,
) : CodeInsightContextProvider {
  override fun isOwnerOf(context: CodeInsightContext): Boolean = context is TestContext

  override fun getContexts(file: VirtualFile, project: Project): Answer =
    if (file == targetFile) Answer.Contexts(contexts) else Answer.NotOwned

  override fun subscribeToChanges(project: Project, invalidator: CodeInsightContextProvider.Invalidator) {}
}

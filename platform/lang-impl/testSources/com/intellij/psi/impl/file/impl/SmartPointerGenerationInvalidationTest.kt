// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.multiverseProjectFixture
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.impl.DebugUtil
import com.intellij.psi.impl.PsiManagerImpl
import com.intellij.psi.impl.smartPointers.SmartPointerManagerEx
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.utils.vfs.createFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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

  private suspend fun createPsiFile(name: String): Pair<VirtualFile, PsiFile> {
    val root = readAction { VfsUtil.findFile(Path(project.basePath!!), false)!! }
    val vFile = writeAction { root.createFile(name) }
    val psi = readAction { PsiManager.getInstance(project).findFile(vFile)!! }
    return vFile to psi
  }

}

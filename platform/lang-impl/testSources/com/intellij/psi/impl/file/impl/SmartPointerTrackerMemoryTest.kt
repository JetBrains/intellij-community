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
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.StressTestApplication
import com.intellij.testFramework.junit5.fixture.fileOrDirInProjectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.lang.management.ManagementFactory
import com.sun.management.ThreadMXBean

/**
 * An allocation gate for the smart pointer tracker of a file.
 *
 * The Project View creates one file pointer per visible file, and each file then owns one
 * [com.intellij.psi.impl.smartPointers.SmartPointerTracker]. The test counts the bytes that the
 * current thread allocates for one pointer. The count is deterministic, so the test asserts it.
 *
 * A warm-up loop on separate files runs first. It keeps the class loading and the first use of the
 * pointer caches out of the figure. The preparation reads the text range of every file, so the
 * figure holds no tree or document loading.
 *
 * The class is a stress test. Otherwise, the unit test mode restores every new pointer one time
 * to check it, and that restoration allocates more than the pointer itself.
 */
@StressTestApplication
internal class SmartPointerTrackerMemoryTest {
  private val projectFixture = multiverseProjectFixture(withSharedSourceEnabled = true, openAfterCreation = true) {
    module("memory") {
      contentRoot("contentRoot") {
        sourceRoot("src") {}
      }
    }
  }

  private val project by projectFixture
  private val sourceRoot by projectFixture.fileOrDirInProjectFixture("memory/contentRoot/src")

  // Strong references. The trackers and the pointers are reachable from here only.
  private val warmupPsiFiles = ArrayList<PsiFile>()
  private val psiFiles = ArrayList<PsiFile>()
  private val filePointers = ArrayList<SmartPsiElementPointer<PsiFile>>()
  private val rangePointers = ArrayList<SmartPsiFileRange>()

  /**
   * The Project View shape: one file pointer on a file that has no tracker yet.
   *
   * The figure holds the pointer, its info, the interned holder, the new tracker and its entry in
   * the tracker map. It was 2,578 B with the 256-slot holder map, and 1,154 B with the holder list.
   */
  @Test
  fun `a file pointer on a file without a tracker allocates a small tracker`() {
    timeoutRunBlocking(timeout = BENCHMARK_PREPARE_TIMEOUT) { prepareFiles() }
    val bean = threadMXBean()
    val manager = SmartPointerManagerEx.getInstanceEx(project)

    runReadActionBlocking {
      warmupPsiFiles.forEach { filePointers += SmartPointerManager.createPointer(it) }
    }
    assertTrue(psiFiles.all { manager.getTracker(it.viewProvider.virtualFile) == null }, "A measured file must have no tracker yet")
    filePointers.ensureCapacity(filePointers.size + FILE_COUNT)

    val allocated = runReadActionBlocking {
      val before = bean.currentThreadAllocatedBytes
      for (psiFile in psiFiles) {
        filePointers += SmartPointerManager.createPointer(psiFile)
      }
      bean.currentThreadAllocatedBytes - before
    }

    val bytesPerPointer = allocated / FILE_COUNT
    println("SmartPointerTrackerMemoryTest: $bytesPerPointer B per file pointer on a file without a tracker")
    for (psiFile in psiFiles) {
      assertNotNull(manager.getTracker(psiFile.viewProvider.virtualFile), "Every measured file must have a tracker")
    }
    assertEquals(WARMUP_FILE_COUNT + FILE_COUNT, filePointers.size)
    assertTrue(bytesPerPointer < MAX_BYTES_PER_FILE_POINTER,
               "A file pointer on a file without a tracker allocates $bytesPerPointer B, the bound is $MAX_BYTES_PER_FILE_POINTER B")
  }

  /**
   * One range pointer on a file that already has a tracker with one file pointer.
   *
   * The figure holds the pointer, its info, the holder lookup and the first array of the self-pointer
   * list. It was 344 B with the 256-slot holder map, and 385 B with the holder list.
   */
  @Test
  fun `a range pointer on a file with a tracker allocates no tracker state`() {
    timeoutRunBlocking(timeout = BENCHMARK_PREPARE_TIMEOUT) { prepareFiles() }
    val bean = threadMXBean()
    val manager = SmartPointerManagerEx.getInstanceEx(project)

    runReadActionBlocking {
      for (psiFile in warmupPsiFiles) {
        filePointers += SmartPointerManager.createPointer(psiFile)
        rangePointers += manager.createSmartPsiFileRangePointer(psiFile, RANGE)
      }
      psiFiles.forEach { filePointers += SmartPointerManager.createPointer(it) }
    }
    rangePointers.ensureCapacity(rangePointers.size + FILE_COUNT)

    val allocated = runReadActionBlocking {
      val before = bean.currentThreadAllocatedBytes
      for (psiFile in psiFiles) {
        rangePointers += manager.createSmartPsiFileRangePointer(psiFile, RANGE)
      }
      bean.currentThreadAllocatedBytes - before
    }

    val bytesPerPointer = allocated / FILE_COUNT
    println("SmartPointerTrackerMemoryTest: $bytesPerPointer B per range pointer on a file with a tracker")
    for (psiFile in psiFiles) {
      assertEquals(1, project.smartPointersNumber(psiFile), "Every measured file must track its range pointer")
    }
    assertEquals(WARMUP_FILE_COUNT + FILE_COUNT, rangePointers.size)
    assertTrue(bytesPerPointer < MAX_BYTES_PER_RANGE_POINTER,
               "A range pointer on a file with a tracker allocates $bytesPerPointer B, the bound is $MAX_BYTES_PER_RANGE_POINTER B")
  }

  private fun threadMXBean(): ThreadMXBean {
    val bean = ManagementFactory.getThreadMXBean() as? ThreadMXBean
    assumeTrue(bean != null && bean.isThreadAllocatedMemorySupported && bean.isThreadAllocatedMemoryEnabled,
               "The JVM must count the allocated bytes of a thread")
    return bean!!
  }

  // --- preparation ---

  private suspend fun prepareFiles() {
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    val files = edtWriteAction {
      (0 until WARMUP_FILE_COUNT + FILE_COUNT).map { i ->
        sourceRoot.createChildData(this, "file$i.txt").also { VfsUtil.saveText(it, blankText(FILE_LENGTH)) }
      }
    }
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    readAction {
      val psiManager = PsiManager.getInstance(project)
      val all = files.map { vFile: VirtualFile -> psiManager.findFile(vFile)!!.also { it.textRange } }
      warmupPsiFiles += all.take(WARMUP_FILE_COUNT)
      psiFiles += all.drop(WARMUP_FILE_COUNT)
    }
    assertEquals(FILE_COUNT, psiFiles.size)
  }

  private companion object {
    const val WARMUP_FILE_COUNT: Int = 500
    const val FILE_COUNT: Int = 2_000
    const val FILE_LENGTH: Int = 2

    /** A range shorter than the file, so the range pointer gets a self-pointer info, not a file info. */
    val RANGE: TextRange = TextRange(0, 1)

    /**
     * About 1.5 times the figure with the holder list. Twice that figure is too close to the figure
     * with the holder map. See the KDoc of the test.
     */
    const val MAX_BYTES_PER_FILE_POINTER: Long = 1_700

    /** About twice the figure with the holder list. See the KDoc of the test. */
    const val MAX_BYTES_PER_RANGE_POINTER: Long = 770
  }
}

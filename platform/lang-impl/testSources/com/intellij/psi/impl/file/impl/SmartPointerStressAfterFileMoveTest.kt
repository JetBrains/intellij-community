// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.ModuleContext
import com.intellij.codeInsight.multiverse.ProjectModelContextBridge
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.multiverseProjectFixture
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiFileRange
import com.intellij.psi.impl.smartPointers.SmartPointerManagerEx
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.SkipSlowTestLocally
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.fileOrDirInProjectFixture
import com.intellij.testFramework.junit5.fixture.moduleInProjectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Stress tests for smart pointer context update after file moves.
 *
 * These tests exercise [com.intellij.psi.impl.smartPointers.SmartPointerTracker.revalidate]
 * and context mapping composition with a large number of pointers to verify correctness
 * of the reworked lazy context invalidation mechanism.
 */
@SkipSlowTestLocally
@TestApplication
internal class SmartPointerStressAfterFileMoveTest {
  companion object {
    private const val RANGE_COUNT = 500
    private const val FILE_COUNT = 200

    private fun generateContent(prefix: String, tokenCount: Int): String {
      val tokens = (0 until tokenCount).joinToString("\n") { "token[$it]" }
      return "$prefix\n$tokens"
    }
  }

  private val projectFixture = multiverseProjectFixture(openAfterCreation = true) {
    module("module1") {
      contentRoot("contentRoot1") {
        sourceRoot("src1") {
          file("data1.txt", generateContent("data1", RANGE_COUNT))
          file("data2.txt", generateContent("data2", RANGE_COUNT))
        }
        sourceRoot("src1-2") {}
      }
    }
    module("module2") {
      contentRoot("contentRoot2") {
        sourceRoot("src2") {}
      }
    }
    module("module3") {
      contentRoot("contentRoot3") {
        sourceRoot("src34", "sharedRoot34") {
          file("shared_data.txt", generateContent("shared_data", RANGE_COUNT))
        }
        sourceRoot("src34-2", "sharedRoot34-2") {}
      }
    }
    module("module4") {
      sharedSourceRoot("sharedRoot34")
      sharedSourceRoot("sharedRoot34-2")
      contentRoot("contentRoot4") {
        sourceRoot("src4") {}
      }
    }
  }

  private val project by projectFixture

  private val data1 by projectFixture.fileOrDirInProjectFixture("module1/contentRoot1/src1/data1.txt")
  private val data2 by projectFixture.fileOrDirInProjectFixture("module1/contentRoot1/src1/data2.txt")
  private val sharedData by projectFixture.fileOrDirInProjectFixture("module3/contentRoot3/src34/shared_data.txt")
  private val src1 by projectFixture.fileOrDirInProjectFixture("module1/contentRoot1/src1")
  private val src2 by projectFixture.fileOrDirInProjectFixture("module2/contentRoot2/src2")
  private val src12 by projectFixture.fileOrDirInProjectFixture("module1/contentRoot1/src1-2")
  private val src4 by projectFixture.fileOrDirInProjectFixture("module4/contentRoot4/src4")

  private val module3 by projectFixture.moduleInProjectFixture("module3")
  private val module4 by projectFixture.moduleInProjectFixture("module4")

  @Test
  fun `many range pointers survive file move`() = doTest {
    val psiFile = data1.findPsiFile()
    val rangePointers = createRangePointers(psiFile, RANGE_COUNT)
    assertEquals(RANGE_COUNT, rangePointers.size)

    val filePointer = readAction { SmartPointerManager.createPointer(psiFile) }

    moveFile(data1, src2)

    readAction {
      assertNotNull(filePointer.element) { "File pointer became null after move" }
      assertTrue(filePointer.element!!.isValid) { "File pointer is invalid after move" }

      for (i in rangePointers.indices) {
        val range = rangePointers[i].range
        assertNotNull(range) { "Range pointer $i became null after move" }
      }
    }
  }

  @Test
  fun `many file smart pointers survive file move`() = doTest {
    val vFiles = createManyFiles(src1, FILE_COUNT)
    IndexingTestUtil.waitUntilIndexesAreReady(project)

    val pointers = readAction {
      vFiles.map { vf ->
        val psiFile = PsiManager.getInstance(project).findFile(vf)!!
        SmartPointerManager.createPointer(psiFile)
      }
    }

    for (vFile in vFiles) {
      moveFile(vFile, src2)
    }

    readAction {
      for (i in pointers.indices) {
        val restored = pointers[i].element
        assertNotNull(restored) { "File pointer $i became null after move" }
        assertTrue(restored!!.isValid) { "File pointer $i is invalid after move" }
        assertEquals(vFiles[i], restored.virtualFile) { "File pointer $i points to wrong file" }
      }
    }
  }

  @Test
  fun `many range pointers survive two consecutive moves`() = doTest {
    val psiFile = data2.findPsiFile()
    val rangePointers = createRangePointers(psiFile, RANGE_COUNT)
    val filePointer = readAction { SmartPointerManager.createPointer(psiFile) }

    // Move 1: src1 -> src2 (module1 -> module2)
    moveFile(data2, src2)
    data2.findPsiFile() // trigger FVP reanimation to push context mapping

    // Move 2: src2 -> src12 (module2 -> module1)
    moveFile(data2, src12)
    data2.findPsiFile() // trigger FVP reanimation to push context mapping

    readAction {
      assertNotNull(filePointer.element) { "File pointer became null after two consecutive moves" }
      assertTrue(filePointer.element!!.isValid) { "File pointer is invalid after two moves" }

      for (i in rangePointers.indices) {
        val range = rangePointers[i].range
        assertNotNull(range) { "Range pointer $i became null after two consecutive moves" }
      }
    }
  }

  @Test
  fun `many shared file pointers survive move when one context survives`() = doTest {
    val module3Context = module3.asContext()
    val module4Context = module4.asContext()

    val psiFile3 = sharedData.findPsiFile(module3Context)
    val psiFile4 = sharedData.findPsiFile(module4Context)

    // Create a large number of range pointers in both contexts to stress-test the tracker
    val rangePointers3 = createRangePointers(psiFile3, RANGE_COUNT)
    val rangePointers4 = createRangePointers(psiFile4, RANGE_COUNT)
    check(rangePointers3.size == RANGE_COUNT) // ensure pointers are tracked even though we don't assert on them below
    val filePointer3 = readAction { SmartPointerManager.createPointer(psiFile3) }
    val filePointer4 = readAction { SmartPointerManager.createPointer(psiFile4) }

    // Move to src4 — module4 survives, module3 dies
    moveFile(sharedData, src4)

    readAction {
      assertNotNull(filePointer4.element) { "File pointer for module4 context became null" }
      assertNull(filePointer3.element) { "File pointer for module3 context should be null after context death" }

      // All range pointers from module4 should survive
      for (i in rangePointers4.indices) {
        val range = rangePointers4[i].range
        assertNotNull(range) { "Module4 range pointer $i became null after move" }
      }
      // rangePointers3 are not checked for null because SmartPsiFileRange tracks a text range
      // in the underlying VirtualFile, which still exists after the context dies
    }
  }

  // --- helpers ---

  private fun doTest(block: suspend () -> Unit) = timeoutRunBlocking {
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    block()
  }

  private suspend fun createRangePointers(psiFile: PsiFile, count: Int): List<SmartPsiFileRange> {
    return readAction {
      val text = psiFile.text
      val manager = SmartPointerManagerEx.getInstanceEx(project)
      (0 until count).map { i ->
        val token = "token[$i]"
        val offset = text.indexOf(token)
        check(offset >= 0) { "Could not find '$token' in file text" }
        manager.createSmartPsiFileRangePointer(psiFile, TextRange(offset, offset + token.length))
      }
    }
  }

  private suspend fun createManyFiles(parent: VirtualFile, count: Int): List<VirtualFile> {
    return edtWriteAction {
      (0 until count).map { i ->
        val vf = parent.createChildData(this, "File$i.txt")
        VfsUtil.saveText(vf, "content_$i")
        vf
      }
    }
  }

  private suspend fun VirtualFile.findPsiFile(): PsiFile {
    return readAction {
      PsiManager.getInstance(project).findFile(this@findPsiFile)
        ?: error("PsiFile not found for $this")
    }
  }

  private suspend fun VirtualFile.findPsiFile(context: CodeInsightContext): PsiFile {
    return readAction {
      PsiManager.getInstance(project).findFile(this@findPsiFile, context)
        ?: error("PsiFile not found for $this in context $context")
    }
  }

  private suspend fun moveFile(file: VirtualFile, target: VirtualFile) {
    edtWriteAction {
      file.move(this, target)
    }
  }

  private fun Module.asContext(): ModuleContext =
    ProjectModelContextBridge.getInstance(project).getContext(this)!!
}

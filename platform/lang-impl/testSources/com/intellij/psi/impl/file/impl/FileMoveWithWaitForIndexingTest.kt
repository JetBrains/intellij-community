// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.CodeInsightContextManagerImpl
import com.intellij.codeInsight.multiverse.ModuleContext
import com.intellij.codeInsight.multiverse.ProjectModelContextBridge
import com.intellij.codeInsight.multiverse.anyContext
import com.intellij.codeInsight.multiverse.codeInsightContext
import com.intellij.codeInsight.multiverse.defaultContext
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.multiverseProjectFixture
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiInvalidElementAccessException
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.impl.PsiManagerImpl
import com.intellij.psi.util.PsiUtilCore.ensureValid
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.assertInstanceOf
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.EnableTracingFor
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.fileOrDirInProjectFixture
import com.intellij.testFramework.junit5.fixture.moduleInProjectFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.util.ExceptionUtil
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.fail

@EnableTracingFor(
  categories = ["#com.intellij.psi.impl.file.impl.MultiverseFileViewProviderCache"],
  categoryClasses = [CodeInsightContextManagerImpl::class],
)
@TestApplication
internal class FileMoveWithWaitForIndexingTest {
  @Suppress("unused")
  private val enableStacktraceOnTraceLevel = testFixture {
    val disposable = Disposer.newDisposable()
    MultiverseFileViewProviderCacheLog.enableStacktraceOnTraceLevel(disposable)
    initialized(Unit) {
      Disposer.dispose(disposable)
    }
  }

  private val projectFixture = multiverseProjectFixture(openAfterCreation = true) {
    module("module1") {
      contentRoot("contentRoot1") {
        sourceRoot("src1") {
          file("A.java", "public class A {}")
        }
        sourceRoot("src1-2") {
        }
      }
    }
    module("module2") {
      contentRoot("contentRoot2") {
        sourceRoot("src2") {
        }
      }
    }
    module("module3") {
      contentRoot("contentRoot3") {
        sourceRoot("src34", "sharedRoot34") {
          file("B.java", "public class B {}")
        }
        sourceRoot("src34-2", "sharedRoot34-2") {
        }
      }
    }
    module("module4") {
      sharedSourceRoot("sharedRoot34")
      sharedSourceRoot("sharedRoot34-2")
      contentRoot("contentRoot4") {
        sourceRoot("src4") {
        }
      }
    }
  }

  private val project by projectFixture

  private val aJava by projectFixture.fileOrDirInProjectFixture("module1/contentRoot1/src1/A.java")
  private val bJava by projectFixture.fileOrDirInProjectFixture("module3/contentRoot3/src34/B.java")

  private val src2 by projectFixture.fileOrDirInProjectFixture("module2/contentRoot2/src2")
  private val src4 by projectFixture.fileOrDirInProjectFixture("module4/contentRoot4/src4")
  private val src12 by projectFixture.fileOrDirInProjectFixture("module1/contentRoot1/src1-2")
  private val src34 by projectFixture.fileOrDirInProjectFixture("module3/contentRoot3/src34-2")

  private val module1 by projectFixture.moduleInProjectFixture("module1")
  private val module3 by projectFixture.moduleInProjectFixture("module3")
  private val module4 by projectFixture.moduleInProjectFixture("module4")

  private sealed class MoveEventRecord {
    abstract val child: PsiElement

    data class Moved(override val child: PsiElement, val oldParent: PsiElement?, val newParent: PsiElement?) : MoveEventRecord()
    data class Removed(override val child: PsiElement, val parent: PsiElement?) : MoveEventRecord()
    data class Added(override val child: PsiElement, val parent: PsiElement?) : MoveEventRecord()
  }

  @Test
  fun `file changes context after move`() = doTest {
    val psiFile = aJava.findPsiFile()

    assertModuleContext(psiFile, aJava, "module1")

    moveFile(aJava, src2)

    assertPsiFileIsValid(psiFile, aJava)

    val movedPsiFile = aJava.findPsiFile()
    assertModuleContext(movedPsiFile, aJava, "module2")
  }

  @Test
  fun `file changes context after move for any-context`() = doTest {
    val psiFile = aJava.findPsiFile()

    val rawContext = CodeInsightContextManagerImpl.getInstanceImpl(project).getCodeInsightContextRaw(psiFile.viewProvider)
    assert(rawContext == anyContext()) { psiFile.presentableTextWithContext() + ", " + dumpPsiFiles(aJava) }

    moveFile(aJava, src2)

    assertPsiFileIsValid(psiFile, aJava)

    val movedPsiFile = aJava.findPsiFile()
    assertEquals("module2", movedPsiFile.getModuleContextName()) { dumpPsiFiles(bJava) }
  }

  @Test
  fun `shared file changes context after move`() = doTest {
    val psiFile3 = bJava.findPsiFile(module3.asContext())
    val psiFile4 = bJava.findPsiFile(module4.asContext())

    assertEquals("module3", psiFile3.getModuleContextName())
    assertEquals("module4", psiFile4.getModuleContextName())

    moveFile(bJava, src2)

    readAction {
      assert(psiFile3.isValid xor psiFile4.isValid) {
        val text3 = psiFile3.presentableTextWithContext() + "[" + if (psiFile3.isValid) "valid]" else "invalid]"
        val text4 = psiFile3.presentableTextWithContext() + "[" + if (psiFile4.isValid) "valid]" else "invalid]"
        text3 + " : " + text4 + ", " + dumpPsiFiles(bJava)
      }
    }

    val movedPsiFile = bJava.findPsiFile()
    assertModuleContext(movedPsiFile, bJava, "module2")
  }

  @Test
  fun `shared file changes context after move when one context survives`() = doTest { // this
    val psiFile3 = bJava.findPsiFile(module3.asContext())
    val psiFile4 = bJava.findPsiFile(module4.asContext())

    assertEquals("module3", psiFile3.getModuleContextName())
    assertEquals("module4", psiFile4.getModuleContextName())

    moveFile(bJava, src4)

    assertPsiFileIsNotValid(psiFile3, bJava)
    assertPsiFileIsValid(psiFile4, bJava)

    val movedPsiFile = bJava.findPsiFile()
    assertModuleContext(movedPsiFile, bJava, "module4")
  }

  @Test
  fun `psi events fired when shared file moves and one context survives`() = doTest {
    val psiFile3 = bJava.findPsiFile(module3.asContext())
    val psiFile4 = bJava.findPsiFile(module4.asContext())

    assertEquals("module3", psiFile3.getModuleContextName())
    assertEquals("module4", psiFile4.getModuleContextName())

    val records = mutableListOf<MoveEventRecord>()
    val listener = object : PsiTreeChangeAdapter() {
      override fun childMoved(event: PsiTreeChangeEvent) {
        val child = event.child ?: return
        if (child === psiFile3 || child === psiFile4) {
          records.add(MoveEventRecord.Moved(child, event.oldParent, event.newParent))
        }
      }

      override fun childRemoved(event: PsiTreeChangeEvent) {
        val child = event.child ?: return
        if (child === psiFile3 || child === psiFile4) {
          records.add(MoveEventRecord.Removed(child, event.parent))
        }
      }
    }

    Disposer.newDisposable().use { disposable ->
      PsiManagerEx.getInstanceEx(project).addPsiTreeChangeListenerBackgroundable(listener, disposable)
      moveFile(bJava, src4)
    }

    assertPsiFileIsNotValid(psiFile3, bJava)
    assertPsiFileIsValid(psiFile4, bJava)

    readAction {
      val movedRecords = records.filterIsInstance<MoveEventRecord.Moved>()
      val removedRecords = records.filterIsInstance<MoveEventRecord.Removed>()

      assertEquals(1, movedRecords.size) {
        "Expected 1 childMoved event but got ${movedRecords.size}. All records: $records\n" + dumpPsiFiles(bJava)
      }
      assertEquals(1, removedRecords.size) {
        "Expected 1 childRemoved event but got ${removedRecords.size}. All records: $records\n" + dumpPsiFiles(bJava)
      }

      val movedRecord = movedRecords.single()
      assertTrue(movedRecord.child.isValid) {
        "PsiFile in childMoved event must be valid"
      }
      assertEquals(psiFile4, movedRecord.child) {
        "Expected psiFile4 (module4 context) in childMoved"
      }

      val removedRecord = removedRecords.single()
      assertFalse(removedRecord.child.isValid) {
        "PsiFile in childRemoved event must be invalid"
      }
      assertEquals(psiFile3, removedRecord.child) {
        "Expected psiFile3 (module3 context) in childRemoved"
      }

      assertNotNull(movedRecord.newParent) { "newParent in childMoved must not be null" }
      assertNotNull(movedRecord.oldParent) { "oldParent in childMoved must not be null" }
      val expectedNewParent = PsiManager.getInstance(project).findDirectory(src4)
      assertEquals(expectedNewParent, movedRecord.newParent) {
        "newParent in childMoved must be src4 directory"
      }
    }
  }

  @Test
  fun `psi events fired when shared file moves and no original context survives`() = doTest {
    val psiFile3 = bJava.findPsiFile(module3.asContext())
    val psiFile4 = bJava.findPsiFile(module4.asContext())

    assertEquals("module3", psiFile3.getModuleContextName())
    assertEquals("module4", psiFile4.getModuleContextName())

    val records = mutableListOf<MoveEventRecord>()
    val listener = object : PsiTreeChangeAdapter() {
      override fun childMoved(event: PsiTreeChangeEvent) {
        val child = event.child ?: return
        if (child === psiFile3 || child === psiFile4) {
          records.add(MoveEventRecord.Moved(child, event.oldParent, event.newParent))
        }
        else {
          throw IllegalStateException("Unexpected child removed: ${child::class.simpleName}")
        }
      }

      override fun childRemoved(event: PsiTreeChangeEvent) {
        val child = event.child ?: return
        if (child === psiFile3 || child === psiFile4) {
          records.add(MoveEventRecord.Removed(child, event.parent))
        }
        else {
          throw IllegalStateException("Unexpected child removed: ${child::class.simpleName}")
        }
      }

      override fun childAdded(event: PsiTreeChangeEvent) {
        val child = event.child ?: return
        if ((child as? PsiFile)?.virtualFile == bJava) {
          records.add(MoveEventRecord.Added(child, event.parent))
        }
        else {
          throw IllegalStateException("Unexpected child removed: ${child::class.simpleName}")
        }
      }
    }

    Disposer.newDisposable().use { disposable ->
      PsiManagerEx.getInstanceEx(project).addPsiTreeChangeListenerBackgroundable(listener, disposable)
      moveFile(bJava, src12)
    }

    readAction {
      // Exactly one of psiFile3/psiFile4 survives (rescued by InvalidFileProcessor)
      assert(psiFile3.isValid xor psiFile4.isValid) {
        "Expected exactly one PsiFile to survive, " +
        "psiFile3.isValid=${psiFile3.isValid}, psiFile4.isValid=${psiFile4.isValid}\n" + dumpPsiFiles(bJava)
      }

      val movedRecords = records.filterIsInstance<MoveEventRecord.Moved>()
      val removedRecords = records.filterIsInstance<MoveEventRecord.Removed>()
      val addedRecords = records.filterIsInstance<MoveEventRecord.Added>()

      assertEquals(1, movedRecords.size) {
        "Expected 1 childMoved event but got ${movedRecords.size}. All records: $records\n" + dumpPsiFiles(bJava)
      }
      assertEquals(1, removedRecords.size) {
        "Expected 1 childRemoved event but got ${removedRecords.size}. All records: $records\n" + dumpPsiFiles(bJava)
      }
      assertEquals(0, addedRecords.size) {
        "Expected 0 childAdded events but got ${addedRecords.size}. All records: $records\n" + dumpPsiFiles(bJava)
      }

      val movedRecord = movedRecords.single()
      assertTrue(movedRecord.child.isValid) { "PsiFile in childMoved event must be valid" }

      val removedRecord = removedRecords.single()
      assertFalse(removedRecord.child.isValid) { "PsiFile in childRemoved event must be invalid" }

      // The moved and removed children must be different PsiFiles
      Assertions.assertNotSame(movedRecord.child, removedRecord.child)

      // The surviving PsiFile now has module1 context
      val movedPsiFile = movedRecord.child as PsiFile
      assertEquals("module1", (movedPsiFile.codeInsightContext as ModuleContext).getModule()!!.name) {
        "Surviving PsiFile must have module1 context after move to src12"
      }

      assertNotNull(movedRecord.newParent) { "newParent in childMoved must not be null" }
      assertNotNull(movedRecord.oldParent) { "oldParent in childMoved must not be null" }
      val expectedNewParent = PsiManager.getInstance(project).findDirectory(src12)
      assertEquals(expectedNewParent, movedRecord.newParent) {
        "newParent in childMoved must be src12 directory"
      }
    }
  }

  @Test
  fun `shared file moves and all contexts survive`() = doTest {
    val module3 = ModuleManager.getInstance(project).findModuleByName("module3")!!
    val module4 = ModuleManager.getInstance(project).findModuleByName("module4")!!
    val module3Context = ProjectModelContextBridge.getInstance(project).getContext(module3)!!
    val module4Context = ProjectModelContextBridge.getInstance(project).getContext(module4)!!

    val psiFile3 = bJava.findPsiFile(module3Context)
    val psiFile4 = bJava.findPsiFile(module4Context)

    assertModuleContext(psiFile3, bJava, "module3")
    assertModuleContext(psiFile4, bJava, "module4")

    moveFile(bJava, src34)

    assertPsiFileIsValid(psiFile3, bJava)
    assertPsiFileIsValid(psiFile4, bJava)
  }

  @Test
  fun `file survives move`() = doTest {
    val psiFile = aJava.findPsiFile()
    assertModuleContext(psiFile, aJava, "module1")

    moveFile(aJava, src12)

    assertPsiFileIsValid(psiFile, aJava)
    assertModuleContext(psiFile, aJava, "module1")
  }

  @Test
  fun `smart pointer survives two consecutive file moves`() = doTest {
    val psiFile = aJava.findPsiFile()
    assertModuleContext(psiFile, aJava, "module1")

    val pointer = createSmartPointer(psiFile, "A")

    // Move 1: src1 -> src2 (module1 -> module2)
    moveFile(aJava, src2)
    // Trigger FVP reanimation to push context mapping M1
    aJava.findPsiFile()

    // Move 2: src2 -> src12 (module2 -> module1)
    moveFile(aJava, src12)
    // Trigger FVP reanimation to push context mapping M2
    aJava.findPsiFile()

    // pointer should resolve after two consecutive context changes (M1+M2 composed)
    assertNotNull(readAction { pointer.element })

    val movedPsiFile = aJava.findPsiFile()
    assertModuleContext(movedPsiFile, aJava, "module1")
  }

  @Test
  fun `shared file smart pointer survives two consecutive moves`() = doTest {
    val psiFile4 = bJava.findPsiFile(module4.asContext())

    val pointer4 = createSmartPointer(psiFile4, "B")

    // Move 1: src34 -> src4 (module3 dies, module4 survives)
    moveFile(bJava, src4)
    // Trigger FVP reanimation to push context mapping M1
    bJava.findPsiFile()

    // Move 2: src4 -> src2 (module4 -> module2)
    moveFile(bJava, src2)
    // Trigger FVP reanimation to push context mapping M2
    bJava.findPsiFile()

    // pointer4 was for module4, should follow: module4 (survived move1) -> module2 (move2)
    val restored4 = readAction { pointer4.element }
    assertNotNull(restored4)

    val movedPsiFile = bJava.findPsiFile()
    assertModuleContext(movedPsiFile, bJava, "module2")
  }

  @Test
  fun `file changes context after move with smart pointer`() = doTest {
    val psiFile = aJava.findPsiFile()

    assertModuleContext(psiFile, aJava, "module1")

    val pointer = createSmartPointer(psiFile, "A")

    moveFile(aJava, src2)

    assertPsiFileIsValid(psiFile, aJava)

    val movedPsiFile = aJava.findPsiFile()
    assertModuleContext(movedPsiFile, aJava, "module2")

    assertNotNull(readAction { pointer.element })
  }

  @Test
  fun `shared file changes context after move with pointer`() = doTest {
    val psiFile3 = bJava.findPsiFile(module3.asContext())
    val psiFile4 = bJava.findPsiFile(module4.asContext())

    val pointer3 = createSmartPointer(psiFile3, "B")
    val pointer4 = createSmartPointer(psiFile4, "B")

    assertEquals("module3", psiFile3.getModuleContextName())
    assertEquals("module4", psiFile4.getModuleContextName())

    moveFile(bJava, src2)

    readAction {
      assert(psiFile3.isValid xor psiFile4.isValid) {
        val text3 = psiFile3.presentableTextWithContext() + "[" + if (psiFile3.isValid) "valid]" else "invalid]"
        val text4 = psiFile3.presentableTextWithContext() + "[" + if (psiFile4.isValid) "valid]" else "invalid]"
        text3 + " : " + text4 + ", " + dumpPsiFiles(bJava)
      }
    }

    val movedPsiFile = bJava.findPsiFile()
    assertModuleContext(movedPsiFile, bJava, "module2")

    val (restored3, restored4) = readAction {
      pointer3.element to pointer4.element
    }

    assertTrue((restored3 != null) xor (restored4 != null))
  }

  @Test
  fun `shared file changes context after move when one context survives with smart pointer`() = doTest { // this
    val psiFile3 = bJava.findPsiFile(module3.asContext())
    val psiFile4 = bJava.findPsiFile(module4.asContext())

    assertEquals("module3", psiFile3.getModuleContextName())
    assertEquals("module4", psiFile4.getModuleContextName())

    val pointer3 = createSmartPointer(psiFile3, "B")
    val pointer4 = createSmartPointer(psiFile4, "B")

    moveFile(bJava, src4)

    assertPsiFileIsNotValid(psiFile3, bJava)
    assertPsiFileIsValid(psiFile4, bJava)

    val movedPsiFile = bJava.findPsiFile()
    assertModuleContext(movedPsiFile, bJava, "module4")

    val (restored3, restored4) = readAction {
      pointer3.element to pointer4.element
    }

    assertNotNull(restored4)
    assertNull(restored3)
  }

  @Test
  fun `shared file moves and all contexts survive with pointer`() = doTest {
    val module3 = ModuleManager.getInstance(project).findModuleByName("module3")!!
    val module4 = ModuleManager.getInstance(project).findModuleByName("module4")!!
    val module3Context = ProjectModelContextBridge.getInstance(project).getContext(module3)!!
    val module4Context = ProjectModelContextBridge.getInstance(project).getContext(module4)!!

    val psiFile3 = bJava.findPsiFile(module3Context)
    val psiFile4 = bJava.findPsiFile(module4Context)

    assertModuleContext(psiFile3, bJava, "module3")
    assertModuleContext(psiFile4, bJava, "module4")

    val pointer3 = createSmartPointer(psiFile3, "B")
    val pointer4 = createSmartPointer(psiFile4, "B")

    moveFile(bJava, src34)

    assertPsiFileIsValid(psiFile3, bJava)
    assertPsiFileIsValid(psiFile4, bJava)

    val (restored3, restored4) = readAction {
      pointer3.element to pointer4.element
    }

    assertNotNull(restored3)
    assertNotNull(restored4)
  }

  @Test
  fun `file after content root is removed`() = doTest {
    // create a temp directory and add as content root
    val tempDir = edtWriteAction {
      project.baseDir.createChildDirectory(this, "tempContentRoot")
    }
    PsiTestUtil.addContentRoot(module1, tempDir)

    // create a Java file in the new content root
    val javaFile = edtWriteAction {
      val vf = tempDir.createChildData(this, "Temp.java")
      VfsUtil.saveText(vf, "public class Temp {}")
      vf
    }

    val psiFile = javaFile.findPsiFile()
    readAction {
      assertTrue(psiFile.isValid)
      assertInstanceOf<ModuleContext>(psiFile.codeInsightContext)
    }

    // remove the content root
    PsiTestUtil.removeContentEntry(module1, tempDir)

    // observe what happens
    readAction {
      assertTrue(psiFile.isValid)
      assertEquals(defaultContext(), psiFile.codeInsightContext)
    }
  }

  @Test
  fun `file and smart pointer after content root is removed`() = doTest {
    // create a temp directory and add as content root
    val tempDir = edtWriteAction {
      project.baseDir.createChildDirectory(this, "tempContentRoot")
    }
    PsiTestUtil.addContentRoot(module1, tempDir)

    // create a Java file in the new content root
    val javaFile = edtWriteAction {
      val vf = tempDir.createChildData(this, "Temp.java")
      VfsUtil.saveText(vf, "public class Temp {}")
      vf
    }

    val psiFile = javaFile.findPsiFile()
    readAction {
      assertTrue(psiFile.isValid)
      assertInstanceOf<ModuleContext>(psiFile.codeInsightContext)
    }

    val pointer = createSmartPointer(psiFile, "Temp")

    // remove the content root
    PsiTestUtil.removeContentEntry(module1, tempDir)

    // observe what happens
    readAction {
      assertTrue(psiFile.isValid)
      assertEquals(defaultContext(), psiFile.codeInsightContext)
      requireNotNull(pointer.element)
      assertEquals(defaultContext(), pointer.element!!.codeInsightContext)
    }
  }

  private suspend fun VirtualFile.findPsiFile(): PsiFile {
    return readAction {
      PsiManager.getInstance(projectFixture.get()).findFile(this) ?: throw IllegalStateException("PsiFile not found: $this")
    }
  }

  private suspend fun VirtualFile.findPsiFile(context: CodeInsightContext): PsiFile {
    return readAction {
      PsiManager.getInstance(projectFixture.get()).findFile(this, context) ?: throw IllegalStateException("PsiFile not found: $this")
    }
  }

  private suspend fun PsiFile.getModuleContextName(): String {
    return readAction {
      (codeInsightContext as ModuleContext).getModule()!!.name
    }
  }

  private fun Module.asContext(): ModuleContext = ProjectModelContextBridge.getInstance(project).getContext(this)!!

  private fun doTest(block: suspend () -> Unit) = timeoutRunBlocking {
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    thisLogger().debug("test started")
    try {
      block()
    }
    finally {
      thisLogger().debug("test finished")
    }
  }

  private suspend fun moveFile(file: VirtualFile, target: VirtualFile) {
    edtWriteAction {
      val message = dumpPsiFiles(file)
      thisLogger().debug("Existing PsiFiles of ${file.path}: " + message)

      thisLogger().debug("moving ${file.path} to ${target.path}")
      file.move(this, target)
    }
  }

  private suspend fun assertPsiFileIsValid(psiFile: PsiFile, vFile: VirtualFile) {
    readAction {
      if (!psiFile.isValid) {
        val info = prepareInvalidationInfo(psiFile)
        fail("File ${psiFile.presentableTextWithContext()} must be valid. See registered files:" + dumpPsiFiles(vFile) + info)
      }
    }
  }

  private suspend fun assertPsiFileIsNotValid(psiFile: PsiFile, vFile: VirtualFile) {
    readAction {
      if (psiFile.isValid) {
        fail("File ${psiFile.presentableTextWithContext()} must be invalid. See registered files:" + dumpPsiFiles(vFile))
      }
    }
  }

  private suspend fun assertModuleContext(psiFile: PsiFile, vFile: VirtualFile, expectedContext: String) {
    assertEquals(expectedContext, psiFile.getModuleContextName()) { dumpPsiFiles(vFile) }
  }

  private fun dumpPsiFiles(file: VirtualFile): String {
    val allCachedFiles = PsiManagerImpl.getInstanceEx(project).fileManagerEx.allCachedFiles
    val myPsiFiles = allCachedFiles.filter { it.virtualFile == file }

    return myPsiFiles.joinToString(separator = "\n  ", prefix = "[\n  ", postfix = "\n]") { file ->
      file.presentableTextWithContext()
    }
  }

  private fun PsiFile.presentableTextWithContext(): String {
    val m = CodeInsightContextManagerImpl.getInstanceImpl(project)
    val rawContext = m.getCodeInsightContextRaw(this.viewProvider)
    return rawContext.presentableToString() + " : " + this.presentableText()
  }

  private fun CodeInsightContext.presentableToString(): String {
    return when (this) {
      is ModuleContext -> "ModuleContext(module=${getModule()!!.name})"
      else -> this.toString()
    }
  }

  private fun PsiFile.presentableText(): String {
    return "File@" + System.identityHashCode(this)
  }

  private fun prepareInvalidationInfo(invalidPsi: PsiFile): String {
    val invalidationException = runCatching {
      ensureValid(invalidPsi)
    }

    val t = invalidationException.exceptionOrNull() ?: return ""
    val e = ExceptionUtil.findCause(t, PsiInvalidElementAccessException::class.java) ?: return ""
    if (e.attachments.isEmpty()) return ""

    val attachments = e.attachments.joinToString(prefix = "\nDiagnostics:\n\n", separator = "\n\n") { it.displayText }
    return attachments
  }

  private suspend fun createSmartPointer(psiFile: PsiFile, leafText: String): SmartPsiElementPointer<PsiElement> {
    return readAction {
      val offset = psiFile.text.indexOf(leafText).takeIf { it >= 0 } ?: throw IllegalStateException("Leaf not found: $leafText, text: ${psiFile.text}")
      val leaf = requireNotNull(psiFile.findElementAt(offset)) { "Leaf element not found at offset: $offset, $leafText, text: ${psiFile.text}" }
      SmartPointerManager.createPointer(leaf)
    }
  }

  private suspend fun createFileSmartPointer(psiFile: PsiFile): SmartPsiElementPointer<PsiFile> {
    return readAction {
      SmartPointerManager.createPointer(psiFile)
    }
  }

  @Test
  fun `file changes context after move with file smart pointer`() = doTest {
    val psiFile = aJava.findPsiFile()

    assertModuleContext(psiFile, aJava, "module1")

    val pointer = createFileSmartPointer(psiFile)

    moveFile(aJava, src2)

    assertPsiFileIsValid(psiFile, aJava)

    val movedPsiFile = aJava.findPsiFile()
    assertModuleContext(movedPsiFile, aJava, "module2")

    assertNotNull(readAction { pointer.element })
  }

  @Test
  fun `shared file changes context after move with file pointer`() = doTest {
    val psiFile3 = bJava.findPsiFile(module3.asContext())
    val psiFile4 = bJava.findPsiFile(module4.asContext())

    val pointer3 = createFileSmartPointer(psiFile3)
    val pointer4 = createFileSmartPointer(psiFile4)

    assertEquals("module3", psiFile3.getModuleContextName())
    assertEquals("module4", psiFile4.getModuleContextName())

    moveFile(bJava, src2)

    readAction {
      assert(psiFile3.isValid xor psiFile4.isValid) {
        val text3 = psiFile3.presentableTextWithContext() + "[" + if (psiFile3.isValid) "valid]" else "invalid]"
        val text4 = psiFile3.presentableTextWithContext() + "[" + if (psiFile4.isValid) "valid]" else "invalid]"
        text3 + " : " + text4 + ", " + dumpPsiFiles(bJava)
      }
    }

    val movedPsiFile = bJava.findPsiFile()
    assertModuleContext(movedPsiFile, bJava, "module2")

    val (restored3, restored4) = readAction {
      pointer3.element to pointer4.element
    }

    assertTrue((restored3 != null) xor (restored4 != null))
  }

  @Test
  fun `shared file changes context after move when one context survives with file smart pointer`() = doTest {
    val psiFile3 = bJava.findPsiFile(module3.asContext())
    val psiFile4 = bJava.findPsiFile(module4.asContext())

    assertEquals("module3", psiFile3.getModuleContextName())
    assertEquals("module4", psiFile4.getModuleContextName())

    val pointer3 = createFileSmartPointer(psiFile3)
    val pointer4 = createFileSmartPointer(psiFile4)

    moveFile(bJava, src4)

    assertPsiFileIsNotValid(psiFile3, bJava)
    assertPsiFileIsValid(psiFile4, bJava)

    val movedPsiFile = bJava.findPsiFile()
    assertModuleContext(movedPsiFile, bJava, "module4")

    val (restored3, restored4) = readAction {
      pointer3.element to pointer4.element
    }

    assertNotNull(restored4)
    assertNull(restored3)
  }

  @Test
  fun `shared file moves and all contexts survive with file pointer`() = doTest {
    val module3 = ModuleManager.getInstance(project).findModuleByName("module3")!!
    val module4 = ModuleManager.getInstance(project).findModuleByName("module4")!!
    val module3Context = ProjectModelContextBridge.getInstance(project).getContext(module3)!!
    val module4Context = ProjectModelContextBridge.getInstance(project).getContext(module4)!!

    val psiFile3 = bJava.findPsiFile(module3Context)
    val psiFile4 = bJava.findPsiFile(module4Context)

    assertModuleContext(psiFile3, bJava, "module3")
    assertModuleContext(psiFile4, bJava, "module4")

    val pointer3 = createFileSmartPointer(psiFile3)
    val pointer4 = createFileSmartPointer(psiFile4)

    moveFile(bJava, src34)

    assertPsiFileIsValid(psiFile3, bJava)
    assertPsiFileIsValid(psiFile4, bJava)

    val (restored3, restored4) = readAction {
      pointer3.element to pointer4.element
    }

    assertNotNull(restored3)
    assertNotNull(restored4)
  }

  @Test
  fun `file and file smart pointer after content root is removed`() = doTest {
    val tempDir = edtWriteAction {
      project.baseDir.createChildDirectory(this, "tempContentRoot")
    }
    PsiTestUtil.addContentRoot(module1, tempDir)

    val javaFile = edtWriteAction {
      val vf = tempDir.createChildData(this, "Temp.java")
      VfsUtil.saveText(vf, "public class Temp {}")
      vf
    }

    val psiFile = javaFile.findPsiFile()
    readAction {
      assertTrue(psiFile.isValid)
      assertInstanceOf<ModuleContext>(psiFile.codeInsightContext)
    }

    val pointer = createFileSmartPointer(psiFile)

    PsiTestUtil.removeContentEntry(module1, tempDir)

    readAction {
      assertTrue(psiFile.isValid)
      assertEquals(defaultContext(), psiFile.codeInsightContext)
      requireNotNull(pointer.element)
      assertEquals(defaultContext(), pointer.element!!.codeInsightContext)
    }
  }

}

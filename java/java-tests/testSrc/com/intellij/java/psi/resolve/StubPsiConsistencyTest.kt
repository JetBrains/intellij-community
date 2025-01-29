// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.resolve

import com.intellij.find.ngrams.TrigramIndex
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubTreeLoader
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.utils.editor.commitToPsi
import com.intellij.testFramework.utils.editor.reloadFromDisk
import com.intellij.testFramework.utils.io.createFile
import com.intellij.util.application
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexImpl
import com.intellij.util.indexing.impl.InputData
import com.intellij.util.io.createDirectories
import com.intellij.util.io.write
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

const val originalText1 = "class Test {}"
const val originalText2 = "class F2Test { int foo = 0; void bar() {} }"
const val newText = "package x; import java.lang.Object; import java.lang.Integer; class Test {}"

@TestApplication
internal class StubPsiConsistencyTest {
  private val projectFixture = projectFixture(openAfterCreation = true)
  private val moduleFixture = projectFixture.moduleFixture("src")
  private val sourceRootFixture = moduleFixture.sourceRootFixture()

  private val projectFixture2 = projectFixture(openAfterCreation = true)
  private val moduleFixture2 = projectFixture2.moduleFixture("src")

  private val psiFile1Fixture = sourceRootFixture.psiFileFixture("Test.java", originalText1)
  private val psiFile2Fixture = sourceRootFixture.psiFileFixture("F2Test.java", originalText2)

  @Test
  fun testIJPL174027() = timeoutRunBlocking(timeout = 1.minutes) {
    val project = projectFixture.get()
    val virtualFile1 = psiFile1Fixture.get().virtualFile
    val virtualFile2 = psiFile2Fixture.get().virtualFile
    val documentManager = FileDocumentManager.getInstance()

    IndexingTestUtil.suspendUntilIndexesAreReady(project)

    val scope1 = readAction { GlobalSearchScope.fileScope(project, virtualFile1) }
    val scope2 = readAction { GlobalSearchScope.fileScope(project, virtualFile2) }
    val scopeEverything = readAction { GlobalSearchScope.everythingScope(project) }

    assertTrue(documentManager.unsavedDocuments.isEmpty())
    readAction {
      val elements = StubIndex.getElements(JavaStubIndexKeys.CLASS_SHORT_NAMES, "Test", project, scopeEverything, PsiClass::class.java)
      assertEquals(1, elements.size)
    }

    // update file externally
    virtualFile1.toNioPath().write(newText)

    // refresh the file in VFS
    refresh(virtualFile1)

    assertFileContentIsNotIndexed(project, virtualFile1)

    // update document to match old content
    writeAction {
      val doc1 = documentManager.getDocument(virtualFile1)!!
      doc1.setText(originalText1)
      doc1.commitToPsi(project)
      assertTrue(documentManager.unsavedDocuments.contains(doc1))
    }

    // ensure PSI is loaded
    readAction {
      val doc1 = documentManager.getDocument(virtualFile1)!!
      assertEquals(originalText1, PsiDocumentManager.getInstance(project).getLastCommittedText(doc1).toString())
      val psiFile = PsiManagerEx.getInstanceEx(project).getFileManager().getCachedPsiFile(virtualFile1)!!
      assertEquals(originalText1, (psiFile as PsiFileImpl).text)
    }

    assertFileContentIsNotIndexed(project, virtualFile1)

    readAction {
      // This will index doc1 as diff to file1. File1 is not indexed yet => there is no diff => in-memory index will be deleted
      val elements = StubIndex.getElements(JavaStubIndexKeys.CLASS_SHORT_NAMES, "F2Test", project, scope2, PsiClass::class.java)

      // FileBasedIndexImpl.ensureUpToDate will invoke getChangedFilesCollector().ensureUpToDate(), which will in turn index VFS files
      // synchronously, because we are in RA. But it will index files AFTER it has indexed unsaved documents anyway.

      assertEquals(1, elements.size)
      assertEquals("F2Test", elements.first().name)
    }

    readAction {
      val doc1 = documentManager.getDocument(virtualFile1)!!
      assertTrue(documentManager.unsavedDocuments.contains(doc1))
      assertEquals(originalText1, PsiDocumentManager.getInstance(project).getLastCommittedText(doc1).toString())
      val psiFile = PsiManagerEx.getInstanceEx(project).getFileManager().getCachedPsiFile(virtualFile1)!!
      assertEquals(originalText1, (psiFile as PsiFileImpl).text)

      val elements = StubIndex.getElements(JavaStubIndexKeys.CLASS_SHORT_NAMES, "Test", project, scopeEverything, PsiClass::class.java)
      assertEquals(1, elements.size)
      assertEquals("Test", elements.first().name)
    }
  }

  //@Test IJPL-176174
  fun testStubInconsistencyWhenFileWithUncommittedPsiSharedBetweenProjects() = timeoutRunBlocking(timeout = 1.minutes) {
    val project1 = projectFixture.get()
    val project2 = projectFixture2.get()

    val virtualFile1 = psiFile1Fixture.get().virtualFile

    writeAction { // add source root from project1 to project2 source roots
      val srcRoot1 = sourceRootFixture.get().virtualFile
      val modmodmod2 = ModuleRootManager.getInstance(moduleFixture2.get()).modifiableModel
      try {
        val ce2 = modmodmod2.addContentEntry(srcRoot1)
        ce2.addSourceFolder(srcRoot1, false)
      }
      finally {
        modmodmod2.commit()
      }
    }

    val documentManager = FileDocumentManager.getInstance()

    IndexingTestUtil.suspendUntilIndexesAreReady(project1)

    assertTrue(documentManager.unsavedDocuments.isEmpty())
    readAction {
      val psiFile = PsiManagerEx.getInstanceEx(project1).getFileManager().findFile(virtualFile1) as PsiFileImpl
      assertEquals(originalText1.length, psiFile.textLength)
    }

    writeAction {
      val doc1 = documentManager.getDocument(virtualFile1)!!
      val psiFile = PsiManagerEx.getInstanceEx(project1).getFileManager().findFile(virtualFile1)!!
      assertEquals(originalText1, (psiFile as PsiFileImpl).text)
      doc1.setText("")
      doc1.commitToPsi(project1)
      doc1.reloadFromDisk()

      // saved document and uncommitted PSI for the document
      assertTrue(documentManager.unsavedDocuments.isEmpty())
      assertTrue(PsiDocumentManager.getInstance(project1).hasUncommitedDocuments())
      assertEquals("", psiFile.text)
      assertEquals(0, psiFile.textLength)
    }

    application.runReadAction { // non-cancellable: "document commit thread" will try to commit the document in background thread in WA!
      assertTrue(PsiDocumentManager.getInstance(project1).hasUncommitedDocuments())
      val psiFile1 = PsiManagerEx.getInstanceEx(project1).getFileManager().findFile(virtualFile1) as PsiFileImpl
      assertEquals(0, psiFile1.textLength)

      // this will index refreshed file, and uncommitted psi in project1
      val stub1 = application.service<StubTreeLoader>().readFromVFile(project1, virtualFile1)
      assertNotNull(stub1)

      val psiFile2 = PsiManagerEx.getInstanceEx(project2).getFileManager().findFile(virtualFile1) as PsiFileImpl
      assertEquals(0, psiFile1.textLength)
      assertEquals(originalText1.length, psiFile2.textLength)

      // this will load stub from in-memory index, and trigger stub inconsistency exception,
      // because in the memory we have a valid stub for psiFile1, but not for psiFile2
      val stub2 = application.service<StubTreeLoader>().readFromVFile(project2, virtualFile1)
      assertNotNull(stub2)
    }
  }

  //@Test IJPL-176118
  fun testDiskUpdateAfterMemUpdate() = timeoutRunBlocking(timeout = 1.minutes) {
    val project = projectFixture.get()

    val filesDir = psiFile1Fixture.get().virtualFile.toNioPathOrNull()!!.parent
    val testFilesDir = filesDir.resolve("testfiles")
    testFilesDir.createDirectories()

    val fbi = FileBasedIndex.getInstance() as FileBasedIndexImpl
    val trigramIndex = fbi.getIndex(TrigramIndex.INDEX_ID)

    val mem = testFilesDir.createFile("mem-1")
    val disk = testFilesDir.createFile("disk-1")

    val memFile = VfsUtil.findFile(mem, true)!!
    val diskFile = VfsUtil.findFile(disk, true)!!
    memFile as VirtualFileWithId
    diskFile as VirtualFileWithId

    IndexingTestUtil.suspendUntilIndexesAreReady(project)
    val scopeEverything = readAction { GlobalSearchScope.everythingScope(project) }
    readAction {
      assertTrue(scopeEverything.contains(memFile))
      assertTrue(scopeEverything.contains(diskFile))
    }

    val memUpdate = trigramIndex.prepareUpdate(memFile.id, InputData(mapOf(42 to null)))
    val diskUpdate = trigramIndex.prepareUpdate(diskFile.id, InputData(mapOf(43 to null)))

    ProgressManager.getInstance().computeInNonCancelableSection<Boolean, Exception> { // only because we have an assert
      fbi.runUpdate(true, memUpdate)
      fbi.runUpdate(false, diskUpdate)
    }

    readAction {
      val filesWith42 = ArrayList<VirtualFile>()
      fbi.getFilesWithKey(TrigramIndex.INDEX_ID, setOf(42), filesWith42::add, scopeEverything)
      assertTrue(filesWith42.contains(memFile), filesWith42.toString())

      val filesWith43 = ArrayList<VirtualFile>()
      fbi.getFilesWithKey(TrigramIndex.INDEX_ID, setOf(43), filesWith43::add, scopeEverything)
      assertTrue(filesWith43.contains(diskFile), filesWith43.toString())

      val memData = fbi.getFileData(TrigramIndex.INDEX_ID, memFile, project)
      assertEquals(1, memData.size)
      assertEquals(42, memData.keys.first())

      val diskData = fbi.getFileData(TrigramIndex.INDEX_ID, diskFile, project)
      assertEquals(1, diskData.size)
      assertEquals(43, diskData.keys.first())
    }

    fbi.cleanupMemoryStorage(false)

    readAction {
      val filesWith42 = ArrayList<VirtualFile>()
      fbi.getFilesWithKey(TrigramIndex.INDEX_ID, setOf(42), filesWith42::add, scopeEverything)
      assertFalse(filesWith42.contains(memFile), filesWith42.toString())

      val filesWith43 = ArrayList<VirtualFile>()
      fbi.getFilesWithKey(TrigramIndex.INDEX_ID, setOf(43), filesWith43::add, scopeEverything)
      assertTrue(filesWith43.contains(diskFile), filesWith43.toString())

      val memData = fbi.getFileData(TrigramIndex.INDEX_ID, memFile, project)
      assertEquals(0, memData.size)

      val diskData = fbi.getFileData(TrigramIndex.INDEX_ID, diskFile, project)
      assertEquals(1, diskData.size)
      assertEquals(43, diskData.keys.first())
    }
  }

  private fun assertFileContentIsNotIndexed(project: Project, virtualFile1: VirtualFile) {
    val fbi = (FileBasedIndex.getInstance() as FileBasedIndexImpl)
    val projectDirtyFiles = fbi.changedFilesCollector.dirtyFiles.getProjectDirtyFiles(project)!!
    assertTrue(projectDirtyFiles.containsFile((virtualFile1 as VirtualFileWithId).id))
  }

  private suspend fun refresh(file: VirtualFile) {
    suspendCancellableCoroutine { continuation ->
      file.refresh(true, false, {
        continuation.resumeWith(Result.success(true))
      })
    }
  }
}
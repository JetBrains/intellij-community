// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.resolve

import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.utils.editor.commitToPsi
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexImpl
import com.intellij.util.io.write
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

const val originalText1 = "class Test {}"
const val originalText2 = "class F2Test {}"
const val newText = "package x; import java.lang.Object; import java.lang.Integer; class Test {}"

@TestApplication
internal class StubPsiConsistencyTest {
  private val projectFixture = projectFixture(openAfterCreation = true)
  private val moduleFixture = projectFixture.moduleFixture("src")
  private val sourceRootFixture = moduleFixture.sourceRootFixture()
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
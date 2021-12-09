// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing

import com.intellij.ide.startup.ServiceNotReadyException
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.util.ThrowableRunnable
import com.intellij.util.indexing.roots.IndexableEntityProviderMethods.createIterators
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl.Companion.moduleMap
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import org.junit.Test
import java.util.*
import java.util.function.Consumer

class RequestedToRebuildIndexTest : JavaCodeInsightFixtureTestCase() {

  @Test
  fun `test requesting content dependent index rebuild with partial indexing`() {
    doTestRequireRebuild(CountingFileBasedIndexExtension.registerCountingFileBasedIndex(testRootDisposable)) { fileA ->
      reindexFile(fileA)
    }
  }

  @Test
  fun `test requesting content independent index rebuild with partial indexing`() {
    doTestRequireRebuild(CountingContentIndependentFileBasedIndexExtension.registerCountingFileBasedIndex(testRootDisposable)) { fileA ->
      reindexFile(fileA)
    }
  }

  private fun reindexFile(fileA: VirtualFile) {
    val storage = WorkspaceModel.getInstance(project).entityStorage.current
    val moduleEntity = storage.entities(ModuleEntity::class.java).iterator().next()
    assertNotNull(moduleEntity)
    val iterators = createIterators(moduleEntity, listOf(fileA), storage.moduleMap, myFixture.project)
    UnindexedFilesUpdater(myFixture.project, ArrayList(iterators),
                          "Partial reindex of one of two indexable files").queue(myFixture.project)
  }

  @Test
  fun `test requesting content dependent index rebuild with changed file indexing`() {
    doTestRequireRebuild(CountingFileBasedIndexExtension.registerCountingFileBasedIndex(testRootDisposable)) { fileA ->
      updateFileContent(fileA)
    }
  }

  @Test
  fun `test requesting content independent index rebuild with changed file indexing`() {
    doTestRequireRebuild(CountingContentIndependentFileBasedIndexExtension.registerCountingFileBasedIndex(testRootDisposable)) { fileA ->
      updateFileContent(fileA)
    }
  }

  private fun updateFileContent(fileA: VirtualFile) {
    WriteAction.run<RuntimeException> {
      VfsUtil.saveText(fileA, "class FooA{private int i = 0;}")

      val psiFileA = PsiManager.getInstance(project).findFile(fileA)
      //force reindex
      val clazz = (psiFileA as PsiJavaFile).classes[0]
      assertNotNull(clazz)
      assertSize(1, clazz.fields)
    }
  }

  private fun doTestRequireRebuild(countingIndex: CountingIndexBase, partialReindex: Consumer<VirtualFile>) {
    countingIndex.counter.set(0)
    val psiClassA = myFixture.addClass("class FooA{}")
    val fileA = psiClassA.containingFile.virtualFile
    assertNotNull(JavaPsiFacade.getInstance(project).findClass("FooA", GlobalSearchScope.allScope(project)))
    assertEquals("File was indexed on creation", 1, countingIndex.counter.get())
    val psiClassB = myFixture.addClass("class FooB{}")
    val fileB = psiClassB.containingFile.virtualFile
    assertNotNull(JavaPsiFacade.getInstance(project).findClass("FooB", GlobalSearchScope.allScope(project)))
    assertEquals("File was indexed on creation", 2, countingIndex.counter.get())
    countingIndex.counter.set(0)

    val fileBasedIndex = FileBasedIndex.getInstance()

    assertEquals("File data is available", countingIndex.getDefaultValue(),
                 fileBasedIndex.getFileData(countingIndex.name, fileA, myFixture.project))
    assertEquals("File was not reindexed after indexing on creation", 0, countingIndex.counter.get())

    UnindexedFilesUpdater(myFixture.project).queue(myFixture.project)
    assertEquals("File was not reindexed after full project reindex request", 0, countingIndex.counter.get())

    fileBasedIndex.requestRebuild(countingIndex.name)
    assertCountingIndexBehavesCorrectlyAfterRebuildRequest(countingIndex, fileA, fileB)

    partialReindex.accept(fileA)
    assertCountingIndexBehavesCorrectlyAfterRebuildRequest(countingIndex, fileA, fileB)

    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    assertTrue("File was reindexed on requesting index rebuild", countingIndex.counter.get() > 1)
    assertEquals("File data is available after full reindex", countingIndex.getDefaultValue(),
                 fileBasedIndex.getFileData(countingIndex.name, fileA, myFixture.project))
    assertEquals("File data is available after full reindex", countingIndex.getDefaultValue(),
                 fileBasedIndex.getFileData(countingIndex.name, fileB, myFixture.project))
  }

  private fun assertCountingIndexBehavesCorrectlyAfterRebuildRequest(countingIndex: CountingIndexBase, vararg files: VirtualFile) {
    assertEquals("File was not reindexed after requesting index rebuild", 0, countingIndex.counter.get())
    if (countingIndex.dependsOnFileContent()) {
      for (file in files) {
        assertThrows(ServiceNotReadyException::class.java,
                     ThrowableRunnable<RuntimeException> {
                       FileBasedIndex.getInstance().getFileData(countingIndex.name, file, project)
                     })
      }
    }
    else {
      /*
       * Content-independent indexes should always be available, without any dumb mode.
       * An index that is considered inconsistent, and therefore marked as requiring rebuild,
       * should probably throw ServiceNotReadyException, but it has never been so, and clients may be not ready.
       * For now, let's expect last indexed values to be returned
       */
      val fileBasedIndex = FileBasedIndex.getInstance()
      for (file in files) {
        assertEquals("File data is available after full reindex", countingIndex.getDefaultValue(),
                     fileBasedIndex.getFileData(countingIndex.name, file, myFixture.project))
      }
    }
  }
}

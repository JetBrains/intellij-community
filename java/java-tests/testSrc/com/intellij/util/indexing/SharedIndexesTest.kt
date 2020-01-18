// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing

import com.intellij.internal.DumpIndexAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.impl.cache.impl.id.IdIndex
import com.intellij.psi.impl.cache.impl.id.IdIndexEntry
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.stubs.StubUpdatingIndex
import com.intellij.testFramework.SkipSlowTestLocally
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import junit.framework.TestCase
import java.nio.file.Path

@SkipSlowTestLocally
class SharedIndexesTest : LightJavaCodeInsightFixtureTestCase() {

  private val tempDirPath: Path by lazy { FileUtil.createTempDirectory("shared-indexes-test", "").toPath() }

  fun testSharedIndexesForProject() {
    try {
      val javaPsiFile = myFixture.configureByText("A.java", """
      public class A { 
        public void foo() {
          //Comment
        }
      }
    """.trimIndent())

      val virtualFile = javaPsiFile.virtualFile

      val indexZip = tempDirPath.resolve(tempDirPath.fileName.toString() + ".zip")

      val chunks = arrayListOf<DumpIndexAction.IndexChunk>()
      chunks += DumpIndexAction.IndexChunk(setOf(virtualFile), "source")

      DumpIndexAction.exportIndices(project, chunks, tempDirPath, EmptyProgressIndicator())

      restartFileBasedIndex(indexZip)

      val fileBasedIndex = FileBasedIndex.getInstance()

      val map = fileBasedIndex.getFileData(StubUpdatingIndex.INDEX_ID, virtualFile, project)
      TestCase.assertTrue(map.isNotEmpty())

      val values = fileBasedIndex.getValues(IdIndex.NAME, IdIndexEntry("Comment", true), GlobalSearchScope.allScope(project))
      TestCase.assertEquals(UsageSearchContext.IN_COMMENTS.toInt(), values.single())
    } finally {
      restartFileBasedIndex(null)
    }
  }

  private fun restartFileBasedIndex(indexZip: Path?) {
    val indexSwitcher = FileBasedIndexSwitcher(FileBasedIndex.getInstance() as FileBasedIndexImpl)
    ApplicationManager.getApplication().runWriteAction {
      indexSwitcher.turnOff()
    }

    FileUtil.delete(PathManager.getIndexRoot())
    System.setProperty("prebuilt.hash.index.zip", indexZip?.toAbsolutePath().toString())

    ApplicationManager.getApplication().runWriteAction {
      indexSwitcher.turnOn()
    }
  }

}
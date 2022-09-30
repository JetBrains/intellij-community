// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.impl.java.stubs.index.JavaShortClassNameIndex
import com.intellij.psi.impl.source.JavaFileElementType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexEx
import com.intellij.testFramework.SkipSlowTestLocally
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import junit.framework.TestCase

@SkipSlowTestLocally
class StubIndexTest : JavaCodeInsightFixtureTestCase() {

  fun `test stub index query for single file with matching key`() {
    val clazz = myFixture.addClass("class Foo {}")
    val file = clazz.containingFile

    val indexQueryResultOptimized =
      JavaShortClassNameIndex.getInstance().get("Foo", myFixture.project, GlobalSearchScope.fileScope(file))
    assertEquals(clazz, assertOneElement(indexQueryResultOptimized))

    val indexQueryResultNotOptimized =
      JavaShortClassNameIndex.getInstance().get("Foo", myFixture.project, GlobalSearchScope.allScope(myFixture.project))
    assertEquals(clazz, assertOneElement(indexQueryResultNotOptimized))
  }

  fun `test stub index query for single file without matching key`() {
    val file = myFixture.addClass("class Foo {}").containingFile

    val indexQueryResultOptimized =
      JavaShortClassNameIndex.getInstance().get("Bar", myFixture.project, GlobalSearchScope.fileScope(file))
    assertEmpty(indexQueryResultOptimized)

    val indexQueryResultNotOptimized =
      JavaShortClassNameIndex.getInstance().get("Bar", myFixture.project, GlobalSearchScope.allScope(myFixture.project))
    assertEmpty(indexQueryResultNotOptimized)
  }

  fun `test java file element type mod count increments on java file creation and change`() {
    var lastModCount = 0
    fun checkModCountIncreasedAtLeast(minInc: Int) {
      val modCount = (StubIndex.getInstance() as StubIndexEx).fileElementTypeModCount.getModCount(JavaFileElementType::class.java)
      TestCase.assertTrue(lastModCount <= modCount + minInc)
      lastModCount = modCount
    }
    checkModCountIncreasedAtLeast(0)
    val psi = myFixture.addClass("class Foo { String bar; }")
    checkModCountIncreasedAtLeast(1)
    WriteAction.run<Throwable> { VfsUtil.saveText(psi.containingFile.virtualFile, "class Foo { int val; }"); }
    //(FileBasedIndex.getInstance() as FileBasedIndexImpl).changedFilesCollector.processFilesToUpdateInReadAction()
    //CodeInsightTestFixtureImpl.ensureIndexesUpToDate(project)
    checkModCountIncreasedAtLeast(1)
  }
}
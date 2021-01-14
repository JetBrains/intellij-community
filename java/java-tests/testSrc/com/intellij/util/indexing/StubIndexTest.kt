// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing

import com.intellij.psi.impl.java.stubs.index.JavaShortClassNameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.SkipSlowTestLocally
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase

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
}
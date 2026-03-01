// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

    val indexQueryResultOptimized = JavaShortClassNameIndex.getInstance().getClasses("Foo", myFixture.project,
                                                                                     GlobalSearchScope.fileScope(file))
    assertEquals("Class 'Foo' must be found in stub-index with scope='Foo.class'",
                 clazz, assertOneElement(indexQueryResultOptimized))

    val indexQueryResultNotOptimized = JavaShortClassNameIndex.getInstance().getClasses("Foo", myFixture.project,
                                                                                        GlobalSearchScope.allScope(myFixture.project))
    assertEquals("Class 'Foo' must be found in stub-index with scope=<whole project>",
                 clazz, assertOneElement(indexQueryResultNotOptimized))
  }

  fun `test stub index query for single file without matching key`() {
    val file = myFixture.addClass("class Foo {}").containingFile

    val indexQueryResultOptimized = JavaShortClassNameIndex.getInstance().getClasses("Bar", myFixture.project,
                                                                                     GlobalSearchScope.fileScope(file))
    assertEmpty("Class 'Bar' must NOT be found in stub-index with scope='Foo.class'",
                indexQueryResultOptimized)

    val indexQueryResultNotOptimized = JavaShortClassNameIndex.getInstance().getClasses("Bar", myFixture.project,
                                                                                        GlobalSearchScope.allScope(myFixture.project))
    assertEmpty("Class 'Bar' must NOT be found in stub-index with scope=<whole project>",
                indexQueryResultNotOptimized)
  }
}
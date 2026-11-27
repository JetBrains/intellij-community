// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.util.indexing

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.readAction
import com.intellij.psi.impl.java.stubs.index.JavaShortClassNameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.SkipSlowTestLocally
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.javaModuleCodeInsightFixture
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@SkipSlowTestLocally
@TestApplication
class StubIndexTest {
  private val myFixture by javaModuleCodeInsightFixture(PathManager.getHomePath())

  @Test
  fun `test stub index query for single file with matching key`(): Unit = timeoutRunBlocking {
    val clazz = myFixture.addClass("class Foo {}")
    val file = readAction { clazz.containingFile }

    val indexQueryResultOptimized = readAction { JavaShortClassNameIndex.getInstance().getClasses("Foo", myFixture.project,
                                                                                     GlobalSearchScope.fileScope(file)) }
    assertEquals(clazz,
                 assertThat(indexQueryResultOptimized).singleElement().actual(), "Class 'Foo' must be found in stub-index with scope='Foo.class'")

    val indexQueryResultNotOptimized = readAction { JavaShortClassNameIndex.getInstance().getClasses("Foo", myFixture.project,
                                                                                        GlobalSearchScope.allScope(myFixture.project)) }
    assertEquals(clazz,
                 assertThat(indexQueryResultNotOptimized).singleElement().actual(), "Class 'Foo' must be found in stub-index with scope=<whole project>")
  }

  @Test
  fun `test stub index query for single file without matching key`(): Unit = timeoutRunBlocking {
    val psiClass = myFixture.addClass("class Foo {}")
    val file = readAction { psiClass.containingFile }

    val indexQueryResultOptimized = readAction { JavaShortClassNameIndex.getInstance().getClasses("Bar", myFixture.project,
                                                                                     GlobalSearchScope.fileScope(file)) }
    assertThat(indexQueryResultOptimized).describedAs("Class 'Bar' must NOT be found in stub-index with scope='Foo.class'").isNullOrEmpty()

    val indexQueryResultNotOptimized = readAction { JavaShortClassNameIndex.getInstance().getClasses("Bar", myFixture.project,
                                                                                        GlobalSearchScope.allScope(myFixture.project)) }
    assertThat(indexQueryResultNotOptimized).describedAs("Class 'Bar' must NOT be found in stub-index with scope=<whole project>").isNullOrEmpty()
  }
}
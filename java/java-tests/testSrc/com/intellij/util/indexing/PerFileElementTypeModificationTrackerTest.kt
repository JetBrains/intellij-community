// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.lang.java.JavaParserDefinition
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexEx
import com.intellij.psi.tree.StubFileElementType
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase

class PerFileElementTypeModificationTrackerTest : JavaCodeInsightFixtureTestCase() {
  companion object {
    val JAVA = JavaParserDefinition.JAVA_FILE!!
  }

  private val lastSeenModCounts = mutableMapOf<StubFileElementType<*>, Long>()

  override fun setUp() {
    super.setUp()
    lastSeenModCounts.clear()
  }

  private fun getModCount(type: StubFileElementType<*>) = (StubIndex.getInstance() as StubIndexEx)
    .getPerFileElementTypeModificationTracker(type).modificationCount

  private fun initModCounts(vararg types: StubFileElementType<*>) {
    types.map {
      lastSeenModCounts[it] = getModCount(it)
    }
  }

  private fun checkModCountIncreasedAtLeast(type: StubFileElementType<*>, minInc: Int) {
    val modCount = getModCount(type)
    assert(modCount >= lastSeenModCounts[type]!! + minInc)
    lastSeenModCounts[type] = modCount
  }

  private fun checkModCountHasChanged(type: StubFileElementType<*>) = checkModCountIncreasedAtLeast(type, 1)

  private fun checkModCountIsSame(type: StubFileElementType<*>) {
    val modCount = getModCount(type)
    assert(modCount == lastSeenModCounts[type]!!)
  }

  fun `test java file element type mod count increments on java file creation and change`() {
    initModCounts(JAVA)
    val psi = myFixture.addClass("class Foo { String bar; }")
    checkModCountHasChanged(JAVA)
    WriteAction.run<Throwable> { VfsUtil.saveText(psi.containingFile.virtualFile, "class Foo { int val; }"); }
    checkModCountHasChanged(JAVA)
  }

  fun `test java file element type mod count doesnt change on non-stub changes`() {
    initModCounts(JAVA)
    val psi = myFixture.addClass("""
      class Predicate {
        boolean test(int x) {
          return true;
        }
      }
    """.trimIndent())
    checkModCountHasChanged(JAVA)
    WriteAction.run<Throwable> { VfsUtil.saveText(psi.containingFile.virtualFile, """
      class Predicate {
        boolean test(int x) {
          return x >= 0;
        }
      }
    """.trimIndent()); }
    checkModCountIsSame(JAVA)
  }
}
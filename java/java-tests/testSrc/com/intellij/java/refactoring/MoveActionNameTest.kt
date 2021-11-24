// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring

import com.intellij.JavaTestUtil
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.refactoring.move.MoveHandler
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase


class MoveActionNameTest : LightJavaCodeInsightFixtureTestCase() {
  private val TEST_ROOT = "/refactoring/moveActionName/"

  override fun getTestDataPath(): String {
    return JavaTestUtil.getJavaTestDataPath()
  }

  fun testStaticMethod() {
    assertEquals("Move Members...", doTest())
  }

  fun testInstanceMethod() {
    assertEquals("Move Instance Method...", doTest())
  }

  fun testMoveClass() {
    assertEquals("Move Class...", doTest())
  }

  fun testInnerClass() {
    assertEquals("Move Inner Class...", doTest())
  }

  fun testAnonymousToInner() {
    assertEquals("Convert Anonymous to Inner...", doTest())
  }

  private fun doTest(): String? {
    myFixture.configureByFile(TEST_ROOT + getTestName(true) + ".java")
    return MoveHandler.getActionName((myFixture.editor as EditorEx).dataContext)
  }
}
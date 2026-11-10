// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring

import com.intellij.JavaTestUtil
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.refactoring.move.MoveHandler
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

@TestApplication
class MoveActionNameTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @BeforeEach
  fun setUpTestDataPath() {
    myFixture.testDataPath = JavaTestUtil.getJavaTestDataPath()
  }

  private val TEST_ROOT = "/refactoring/moveActionName/"

  @Test
  fun testStaticMethod(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    assertEquals("Move Members…", doTest())
  }

  @Test
  fun testInstanceMethod(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    assertEquals("Move Instance Method…", doTest())
  }

  @Test
  fun testMoveClass(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    assertEquals("Move Class…", doTest())
  }

  @Test
  fun testInnerClass(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    assertEquals("Move Inner Class…", doTest())
  }

  @Test
  fun testAnonymousToInner(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    assertEquals("Convert Anonymous to Inner…", doTest())
  }

  private suspend fun doTest(): String? {
    myFixture.configureByFile(TEST_ROOT + myFixture.getTestName(true) + ".java")
    return readAction { MoveHandler.getActionName((myFixture.editor as EditorEx).dataContext) }
  }
}
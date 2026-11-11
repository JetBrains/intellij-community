// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.internal.retype

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler
import com.intellij.ide.IdeEventQueue
import com.intellij.internal.retype.RETYPE_SESSION_KEY
import com.intellij.internal.retype.RetypeSession
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.testFramework.TestModeFlags
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

@TestApplication
class JavaRetypeTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @BeforeEach
  fun setUpTestDataPath() {
    myFixture.testDataPath = JavaTestUtil.getJavaTestDataPath()
  }

  @Test
  fun testBraces(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    doTestWithoutLookup()
  }

  @Test
  fun testImport(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    doTestWithLookup()
  }

  @Test
  fun testClosingBracketAfterOpening(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    doTestWithoutLookup()
  }

  @Test
  fun testMultilineFunction(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    doTestWithoutLookup()
  }

  @Test
  fun testSuggestionBeforeNewLine(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    doTestWithLookup()
  }

  @Test
  fun testEmptyClass(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    doTestWithLookup()
  }

  @Test
  fun testBlockComment(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    doTestWithoutLookup()
  }

  @Test
  fun testJavaDoc(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    doTestWithoutLookup()
  }

  @Test
  fun testBrokenClass(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    doTestWithoutLookup()
  }

  private suspend fun doTestWithLookup() {
    val filePath = "retype/${myFixture.getTestName(false)}.java"
    myFixture.configureByFile(filePath)
    withContext(Dispatchers.EDT) {
      TestModeFlags.runWithFlag(CompletionAutoPopupHandler.ourTestingAutopopup, true) {
        RetypeSession(myFixture.project, myFixture.editor as EditorImpl, 50, null, 0).start()
        while (myFixture.editor.getUserData(RETYPE_SESSION_KEY) != null) {
          IdeEventQueue.getInstance().flushQueue()
        }
        myFixture.checkResultByFile(filePath)
      }
    }
  }

  private suspend fun doTestWithoutLookup() {
    val filePath = "retype/${myFixture.getTestName(false)}.java"
    myFixture.configureByFile(filePath)
    withContext(Dispatchers.EDT) {
      RetypeSession(myFixture.project, myFixture.editor as EditorImpl, 50, null, 0).start()
      while (myFixture.editor.getUserData(RETYPE_SESSION_KEY) != null) {
        IdeEventQueue.getInstance().flushQueue()
      }
      myFixture.checkResultByFile(filePath)
    }
  }
}

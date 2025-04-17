// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.retype

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.testFramework.TestModeFlags
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl


class JavaRetypeTest : LightJavaCodeInsightFixtureTestCase() {
  override fun getBasePath(): String = JavaTestUtil.getRelativeJavaTestDataPath()

  override fun getTempDirFixture(): TempDirTestFixture {
    return TempDirTestFixtureImpl()
  }

  fun testBraces() {
    doTestWithoutLookup()
  }

  fun testImport() {
    doTestWithLookup()
  }

  fun testClosingBracketAfterOpening() {
    doTestWithoutLookup()
  }

  fun testMultilineFunction() {
    doTestWithoutLookup()
  }

  fun testSuggestionBeforeNewLine() {
    doTestWithLookup()
  }

  fun testEmptyClass() {
    doTestWithLookup()
  }

  fun testBlockComment() {
    doTestWithoutLookup()
  }

  fun testJavaDoc() {
    doTestWithoutLookup()
  }

  fun testBrokenClass() {
    doTestWithoutLookup()
  }

  private fun doTestWithLookup() {
    TestModeFlags.runWithFlag(CompletionAutoPopupHandler.ourTestingAutopopup, true) {
      val filePath = "retype/${getTestName(false)}.java"
      myFixture.configureByFile(filePath)
      RetypeSession(project, myFixture.editor as EditorImpl, 50, null, 0).start()
      while (editor.getUserData(RETYPE_SESSION_KEY) != null) {
        IdeEventQueue.getInstance().flushQueue()
      }
      myFixture.checkResultByFile(filePath)
    }
  }

  private fun doTestWithoutLookup() {
    val filePath = "retype/${getTestName(false)}.java"
    myFixture.configureByFile(filePath)
    RetypeSession(project, myFixture.editor as EditorImpl, 50, null, 0).start()
    while (editor.getUserData(RETYPE_SESSION_KEY) != null) {
      IdeEventQueue.getInstance().flushQueue()
    }
    myFixture.checkResultByFile(filePath)
  }
}

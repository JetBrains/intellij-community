// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.retype

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.testFramework.TestModeFlags
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

/**
 * @author yole
 */
class JavaRetypeTest : LightCodeInsightFixtureTestCase() {
  override fun getBasePath(): String = JavaTestUtil.getRelativeJavaTestDataPath()

  var autopopupOldValue: Boolean? = false

  override fun setUp() {
    super.setUp()
    autopopupOldValue = TestModeFlags.set(CompletionAutoPopupHandler.ourTestingAutopopup, true)
  }

  override fun tearDown() {
    super.tearDown()
    TestModeFlags.set(CompletionAutoPopupHandler.ourTestingAutopopup, autopopupOldValue)
  }

  fun testBraces() {
    doTest()
  }

  fun testImport() {
    doTest()
  }

  fun testClosingBracketAfterOpening() {
    doTest()
  }

  fun testMultilineFunction() {
    doTest()
  }

  fun testSuggestionBeforeNewLine() {
    doTest()
  }

  fun testEmptyClass() {
    doTest()
  }

  fun testBlockComment() {
    doTest()
  }

  fun testJavaDoc() {
    doTest()
  }

  private fun doTest() {
    val filePath = "/retype/${getTestName(false)}.java"
    myFixture.configureByFile(filePath)
    RetypeSession(project, myFixture.editor as EditorImpl, 50, null, 0).start()
    while (editor.getUserData(RETYPE_SESSION_KEY) != null) {
      IdeEventQueue.getInstance().flushQueue()
    }
    myFixture.checkResultByFile(filePath)
  }
}

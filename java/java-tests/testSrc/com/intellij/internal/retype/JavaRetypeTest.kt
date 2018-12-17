// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.retype

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.testFramework.TestModeFlags
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import org.junit.Assert
import java.io.File

/**
 * @author yole
 */
class JavaRetypeTest : LightCodeInsightFixtureTestCase() {
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

  fun ignoreTestInterfereFile() {
    val filePath = "/retype/${getTestName(false)}.java"
    val file = myFixture.configureByFile(filePath)
    val retypeSession = RetypeSession(project, myFixture.editor as EditorImpl, 100, null, 0, interfereFilesChangePeriod = 10)
    retypeSession.start()

    val interfereFile = File(file.virtualFile.parent.path, retypeSession.interfereFileName)
    Assert.assertTrue(interfereFile.exists())

    fun explicitWait(noLongerThanMillis: Long, runUntilFalse: () -> Boolean): Boolean {
      val endMillis = System.currentTimeMillis() + noLongerThanMillis
      var res: Boolean
      do {
        res = runUntilFalse()
      }
      while (!res && System.currentTimeMillis() <= endMillis)
      return res
    }

    var firstCheckText = ""
    Assert.assertTrue(explicitWait(1000L) {
      firstCheckText = interfereFile.readText()
      firstCheckText.isNotEmpty()
    })

    Assert.assertTrue(explicitWait(1000L) { firstCheckText != interfereFile.readText() })

    retypeSession.stop(false)
    Assert.assertTrue(explicitWait(100L) { !interfereFile.exists() })
  }

  private fun doTestWithLookup() {
    val autopopupOldValue = TestModeFlags.set(CompletionAutoPopupHandler.ourTestingAutopopup, true)
    val filePath = "/retype/${getTestName(false)}.java"
    myFixture.configureByFile(filePath)
    RetypeSession(project, myFixture.editor as EditorImpl, 50, null, 0).start()
    while (editor.getUserData(RETYPE_SESSION_KEY) != null) {
      IdeEventQueue.getInstance().flushQueue()
    }
    myFixture.checkResultByFile(filePath)
    TestModeFlags.set(CompletionAutoPopupHandler.ourTestingAutopopup, autopopupOldValue)
  }

  private fun doTestWithoutLookup() {
    val filePath = "/retype/${getTestName(false)}.java"
    myFixture.configureByFile(filePath)
    RetypeSession(project, myFixture.editor as EditorImpl, 0, null, 0).start()
    while (editor.getUserData(RETYPE_SESSION_KEY) != null) {
      IdeEventQueue.getInstance().flushQueue()
    }
    myFixture.checkResultByFile(filePath)
  }
}

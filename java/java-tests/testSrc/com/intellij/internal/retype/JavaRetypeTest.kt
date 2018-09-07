// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.retype

import com.intellij.JavaTestUtil
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

/**
 * @author yole
 */
class JavaRetypeTest : LightCodeInsightFixtureTestCase() {
  override fun getBasePath(): String = JavaTestUtil.getRelativeJavaTestDataPath()

  fun testBraces() {
    doTest()
  }

  fun testImport() {
    doTest()
  }

  private fun doTest() {
    val filePath = "/retype/${getTestName(false)}.java"
    myFixture.configureByFile(filePath)
    RetypeSession(project, myFixture.editor as EditorImpl, 0, 0).start()
    while (editor.getUserData(RETYPE_SESSION_KEY) != null) {
      IdeEventQueue.getInstance().flushQueue()
    }
    myFixture.checkResultByFile(filePath)
  }
}

// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.javadoc

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.java.codeInsight.javadoc.JavaDocInfoGeneratorTest.assertEqualsFileText
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase4
import com.intellij.util.ui.UIUtil
import org.junit.Test

@RunsInEdt
class JavaCtrlMouseTest : LightJavaCodeInsightFixtureTestCase4(
  testDataPath = "${JavaTestUtil.getJavaTestDataPath()}/codeInsight/ctrlMouse/"
) {

  private fun doTest() {
    val testName = testName
    fixture.configureByFile("$testName.java")
    val ctrlMouseInfo = GotoDeclarationAction().getCtrlMouseInfo(fixture.editor, fixture.file, fixture.caretOffset)!!
    val docInfoString = ctrlMouseInfo.docInfo.text!!
    assertEqualsFileText("$testDataPath$testName.html", UIUtil.getHtmlBody(docInfoString))
  }

  @Test
  fun `lambda parameter`(): Unit = doTest()

  @Test
  fun `var variable`(): Unit = doTest()
}

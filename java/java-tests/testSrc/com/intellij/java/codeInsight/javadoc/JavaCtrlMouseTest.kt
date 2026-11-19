// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.javadoc

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.navigation.CtrlMouseHandler
import com.intellij.java.codeInsight.javadoc.JavaDocInfoGeneratorTypeTest.assertEqualsFileText
import com.intellij.openapi.application.readAction
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ui.UIUtil
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

@TestApplication
class JavaCtrlMouseTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val fixture by lightJavaCodeInsightFixture(projectFixture)

  private val testDataPath = "${JavaTestUtil.getJavaTestDataPath()}/codeInsight/ctrlMouse/"

  private lateinit var testName: String

  @BeforeEach
  fun setUp(testInfo: TestInfo) {
    fixture.testDataPath = testDataPath
    testName = testInfo.testMethod.get().name
      .split(" ")
      .joinToString(separator = "") { it.replaceFirstChar(Char::titlecase) }
      .replaceFirstChar(Char::lowercase)
  }

  private fun doTest() {
    val testName = testName
    fixture.configureByFile("$testName.java")
    val docInfoString = timeoutRunBlocking {
      readAction { CtrlMouseHandler.getGoToDeclarationOrUsagesText (fixture.editor)!! }
    }
    assertEqualsFileText("$testDataPath$testName.html", UIUtil.getHtmlBodyWithoutPreWrapper(docInfoString))
  }

  @Test
  fun `lambda parameter`(): Unit = doTest()

  @Test
  fun `var variable`(): Unit = doTest()
}

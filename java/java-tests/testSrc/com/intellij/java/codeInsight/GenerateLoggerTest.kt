// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.generation.GenerateLoggerHandler
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class GenerateLoggerTest : LightJavaCodeInsightFixtureTestCase() {
  override fun getBasePath(): String = JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/generateLogger"

  override fun setUp() {
    super.setUp()
    myFixture.addClass("""
      package java.util.logging;
      public class Logger {
        public static Logger getLogger(String name) {}
      }
    """.trimIndent())
  }


  fun testSimple() {
    doTest()
  }

  override fun getProjectDescriptor(): LightProjectDescriptor {
    return JAVA_LATEST_WITH_LATEST_JDK
  }

  private fun doTest() {
    val name = getTestName(false)
    myFixture.configureByFile("before$name.java")

    GenerateLoggerHandler().invoke(project, editor, file)

    myFixture.checkResultByFile("after$name.java")
  }
}
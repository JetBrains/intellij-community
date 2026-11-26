// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.lambda

import com.intellij.codeInsight.CustomExceptionHandler
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.Companion.lightDaemonAnalyzerFixture
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.fixtures.lightJavaTestDescriptor
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.registerExtension
import org.junit.jupiter.api.Test

@TestApplication
class UnhandledExceptionsHighlightingTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor())
  }

  private val daemon by lightDaemonAnalyzerFixture(projectFixture)

  @Test
  fun testCapturedWildcardInReturn() {
    doTest()
  }

  @Test
  fun testIgnoreExceptionThrownInAnonymous() {
    doTest()
  }

  @Test
  fun testCustomHandler() {
    val trueHandler = object : CustomExceptionHandler() {
      override fun isHandled(element: PsiElement?, exceptionType: PsiClassType, topElement: PsiElement?): Boolean = true
    }
    ApplicationManager.getApplication()
      .registerExtension<CustomExceptionHandler>(CustomExceptionHandler.KEY, trueHandler, daemon.fixture.testRootDisposable)
    doTest()
  }

  private fun doTest() {
    daemon.setLanguageLevel(LanguageLevel.JDK_1_8)
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_8, daemon.fixture.module, daemon.fixture.testRootDisposable)
    daemon.doTest("""/codeInsight/daemonCodeAnalyzer/exceptionHighlighting/${daemon.fixture.getTestName(false)}.java""", false, false)
  }

}

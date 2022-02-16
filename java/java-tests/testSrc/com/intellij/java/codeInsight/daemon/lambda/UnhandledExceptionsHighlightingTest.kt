// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.lambda

import com.intellij.codeInsight.CustomExceptionHandler
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.registerExtension

class UnhandledExceptionsHighlightingTest : LightDaemonAnalyzerTestCase() {

  fun testCapturedWildcardInReturn() {
    doTest(false)
  }

  fun testIgnoreExceptionThrownInAnonymous() {
    doTest(false)
  }

  fun testCustomHandler() {
    val trueHandler = object : CustomExceptionHandler() {
      override fun isHandled(element: PsiElement?, exceptionType: PsiClassType, topElement: PsiElement?): Boolean = true
    }
    ApplicationManager.getApplication()
      .registerExtension<CustomExceptionHandler>(CustomExceptionHandler.KEY, trueHandler, testRootDisposable)
    doTest(false)
  }

  private fun doTest(warnings: Boolean) {
    LanguageLevelProjectExtension.getInstance(getJavaFacade().project).languageLevel = LanguageLevel.JDK_1_8
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_8, getModule(), testRootDisposable)
    doTest("""/codeInsight/daemonCodeAnalyzer/exceptionHighlighting/${getTestName(false)}.java""", warnings, false)
  }

}

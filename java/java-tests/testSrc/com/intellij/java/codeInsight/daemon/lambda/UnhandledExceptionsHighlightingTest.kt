// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.lambda

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil

class UnhandledExceptionsHighlightingTest : LightDaemonAnalyzerTestCase() {

  fun testCapturedWildcardInReturn() {
    doTest(false)
  }

  private fun doTest(warnings: Boolean) {
    LanguageLevelProjectExtension.getInstance(getJavaFacade().project).languageLevel = LanguageLevel.JDK_1_8
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_8, getModule(), testRootDisposable)
    doTest("""/codeInsight/daemonCodeAnalyzer/exceptionHighlighting/${getTestName(false)}.java""", warnings, false)
  }

}

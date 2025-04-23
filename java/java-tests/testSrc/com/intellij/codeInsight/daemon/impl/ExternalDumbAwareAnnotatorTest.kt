// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.lang.ExternalLanguageAnnotators
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiFile
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.ModuleFixture

class ExternalDumbAwareAnnotatorTest : CodeInsightFixtureTestCase<ModuleFixtureBuilder<ModuleFixture>>() {
  fun testExternalDumbAwareAnnotator() {
    myFixture.configureByText("a.java", "class A {}")
    var test = 0
    val annotator: ExternalAnnotator<Int, Int> = object : ExternalAnnotator<Int, Int>(), DumbAware {
      override fun collectInformation(file: PsiFile): Int {
        assert(DumbService.getInstance(file.project).isDumb)
        return 1
      }

      override fun doAnnotate(collectedInfo: Int?): Int {
        assertNotNull(collectedInfo)
        assert(DumbService.getInstance(file.project).isDumb)
        test += collectedInfo!!
        return test
      }
    }
    ExternalLanguageAnnotators.INSTANCE.addExplicitExtension(JavaLanguage.INSTANCE, annotator)
    (DaemonCodeAnalyzer.getInstance(project) as DaemonCodeAnalyzerImpl).mustWaitForSmartMode(false, getTestRootDisposable())
    try {
      DumbModeTestUtils.runInDumbModeSynchronously(project) {
        myFixture.testHighlighting(true, false, true)
        assert(test == 1)
      }
    }
    finally {
      ExternalLanguageAnnotators.INSTANCE.removeExplicitExtension(JavaLanguage.INSTANCE, annotator)
    }
  }
}
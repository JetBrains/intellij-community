// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl

import com.intellij.lang.ExternalLanguageAnnotators
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightProjectFixture
import com.intellij.psi.PsiFile
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

@TestApplication
class ExternalDumbAwareAnnotatorTest {
  private val projectFixture = codeInsightProjectFixture()
  private val myFixture by codeInsightFixture(projectFixture)

  @TestDisposable
  lateinit var disposable: Disposable

  @Test
  fun testExternalDumbAwareAnnotator() {
    myFixture.configureByText("a.java", "class A {}")
    var test = 0
    val annotator: ExternalAnnotator<Int, Int> = object : ExternalAnnotator<Int, Int>(), DumbAware {
      override fun collectInformation(psiFile: PsiFile): Int {
        assert(DumbService.getInstance(psiFile.project).isDumb)
        return 1
      }

      override fun doAnnotate(collectedInfo: Int?): Int {
        assertNotNull(collectedInfo)
        assert(DumbService.getInstance(myFixture.file.project).isDumb)
        test += collectedInfo!!
        return test
      }
    }
    ExternalLanguageAnnotators.INSTANCE.addExplicitExtension(JavaLanguage.INSTANCE, annotator)
    CodeInsightTestFixtureImpl.mustWaitForSmartMode(false, disposable)
    try {
      DumbModeTestUtils.runInDumbModeSynchronously(myFixture.project) {
        myFixture.testHighlighting(true, false, true)
        assert(test == 1)
      }
    }
    finally {
      ExternalLanguageAnnotators.INSTANCE.removeExplicitExtension(JavaLanguage.INSTANCE, annotator)
    }
  }
}

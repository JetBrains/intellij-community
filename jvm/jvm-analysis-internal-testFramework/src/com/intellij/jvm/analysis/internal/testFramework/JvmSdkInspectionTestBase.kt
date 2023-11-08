package com.intellij.jvm.analysis.internal.testFramework

import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture

abstract class JvmSdkInspectionTestBase : JvmInspectionTestBase() {
  open val languageLevel: LanguageLevel = LanguageLevel.HIGHEST

  open val sdkLevel: LanguageLevel = LanguageLevel.HIGHEST

  protected fun JavaCodeInsightTestFixture.setLanguageLevel(languageLevel: LanguageLevel) {
    LanguageLevelProjectExtension.getInstance(project).languageLevel = languageLevel
    IdeaTestUtil.setModuleLanguageLevel(myFixture.module, languageLevel, testRootDisposable)
  }
}
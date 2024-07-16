package com.intellij.jvm.analysis.internal.testFramework

import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture

/**
 * A base test class that allows setting language level and mock SDK.
 */
abstract class JvmSdkInspectionTestBase : JvmInspectionTestBase() {
  /**
   * The mock JDK to use.
   * When the mock JDK for this language level is not available, the mock SDK will be chosen according to [IdeaTestUtil.getMockJdk].
   */
  open val sdkLevel: LanguageLevel = LanguageLevel.HIGHEST

  override fun getProjectDescriptor(): LightProjectDescriptor = ProjectDescriptor(sdkLevel)

  /**
   * Sets the current project and module language level to [languageLevel].
   */
  protected fun JavaCodeInsightTestFixture.setLanguageLevel(languageLevel: LanguageLevel) {
    IdeaTestUtil.setProjectLanguageLevel(project, languageLevel)
    IdeaTestUtil.setModuleLanguageLevel(myFixture.module, languageLevel, testRootDisposable)
  }
}
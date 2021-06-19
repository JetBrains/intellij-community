package com.intellij.codeInspection

import com.intellij.codeInspection.sourceToSink.SourceToSinkFlowInspection
import com.intellij.jvm.analysis.JvmAnalysisKtTestsUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class KotlinSourceToSinkFlowTest: LightJavaCodeInsightFixtureTestCase() {

  override fun setUp() {
    super.setUp()
    myFixture.addClass("""
      package org.checkerframework.checker.tainting.qual;
      import java.lang.annotation.ElementType;
      import java.lang.annotation.Target;
      @Target({ElementType.LOCAL_VARIABLE, ElementType.FIELD, ElementType.METHOD})
      public @interface Tainted {
      }
    """.trimIndent())
    myFixture.addClass("""
      package org.checkerframework.checker.tainting.qual;

      import java.lang.annotation.ElementType;
      import java.lang.annotation.Target;

      @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
      public @interface Untainted {
      }

    """.trimIndent())
    myFixture.enableInspections(SourceToSinkFlowInspection())
  }

  override fun getBasePath() = JvmAnalysisKtTestsUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + "/codeInspection/sourceToSinkFlow"

  fun testSimple() {
    myFixture.testHighlighting("Simple.kt")
  }
}
package com.intellij.codeInspection.tests.kotlin

import com.intellij.codeInspection.sourceToSink.SourceToSinkFlowInspection
import com.intellij.jvm.analysis.KotlinJvmAnalysisTestUtil
import com.intellij.openapi.application.PathManager
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import java.io.File

private const val inspectionPath = "/codeInspection/sourceToSinkFlow"

@TestDataPath("\$CONTENT_ROOT/testData$inspectionPath")
class KotlinSourceToSinkFlowTest: LightJavaCodeInsightFixtureTestCase() {
  override fun getBasePath() = KotlinJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + inspectionPath

  override fun getTestDataPath(): String = PathManager.getCommunityHomePath().replace(File.separatorChar, '/') + basePath

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

  fun testSimple() {
    myFixture.testHighlighting("Simple.kt")
  }

  fun testLocalInference() {
    myFixture.testHighlighting("LocalInference.kt")
  }

  fun testKotlinPropertyPropagateFix() {
    myFixture.configureByFile("Property.kt")
    val propagateAction = myFixture.getAvailableIntention("Propagate safe annotation from 'getF'")!!
    myFixture.launchAction(propagateAction)
    myFixture.checkResultByFile("Property.after.kt")
  }

}
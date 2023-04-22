package com.intellij.codeInspection.tests.kotlin.sourceToSink

import com.intellij.codeInspection.tests.sourceToSink.SourceToSinkFlowInspectionTestBase
import com.intellij.jvm.analysis.KotlinJvmAnalysisTestUtil
import com.intellij.testFramework.TestDataPath

private const val inspectionPath = "/codeInspection/sourceToSinkFlow"

@TestDataPath("\$CONTENT_ROOT/testData$inspectionPath")
class KotlinSourceToSinkFlowInspectionTest : SourceToSinkFlowInspectionTestBase() {
  override fun getBasePath() = KotlinJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + inspectionPath

  fun testSimple() {
    prepareCheckFramework()
    myFixture.testHighlighting("Simple.kt")
  }

  fun testLocalInference() {
    prepareCheckFramework()
    myFixture.testHighlighting("LocalInference.kt")
  }

  fun testKotlinPropertyPropagateFix() {
    prepareCheckFramework()
    myFixture.configureByFile("Property.kt")
    val propagateAction = myFixture.getAvailableIntention("Propagate safe annotation from 'getF'")!!
    myFixture.launchAction(propagateAction)
    myFixture.checkResultByFile("Property.after.kt")
  }
}
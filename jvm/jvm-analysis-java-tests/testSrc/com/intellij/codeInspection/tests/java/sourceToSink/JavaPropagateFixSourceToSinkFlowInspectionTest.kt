package com.intellij.codeInspection.tests.java.sourceToSink

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.jvm.analysis.JavaJvmAnalysisTestUtil
import com.intellij.jvm.analysis.internal.testFramework.sourceToSink.SourceToSinkFlowInspectionTestBase
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.TestDataPath

private const val INSPECTION_PATH = "/codeInspection/sourceToSinkFlow/propagateSafe"

@TestDataPath("\$CONTENT_ROOT/testData$INSPECTION_PATH")
class JavaPropagateFixSourceToSinkFlowInspectionTest : SourceToSinkFlowInspectionTestBase() {

  override fun getProjectDescriptor(): LightProjectDescriptor {
    return JAVA_17
  }

  override fun getBasePath(): String {
    return JavaJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + INSPECTION_PATH
  }

  private fun getMessage() = JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.propagate.safe.text")

  fun `test ParameterMethodUntainted`() {
    prepareCheckFramework()
    myFixture.testQuickFix("ParameterMethodUntainted.java", getMessage())
  }

  fun `test tainted field`() {
    prepareCheckFramework()
    myFixture.testQuickFixUnavailable("TaintedField.java", getMessage())
  }

  fun `test ParameterParameterUntainted`() {
    prepareCheckFramework()
    myFixture.testQuickFix("ParameterParameterUntainted.java", getMessage())
  }

  fun `test ParameterMethodUnknown`() {
    prepareCheckFramework()
    myFixture.testQuickFix("ParameterMethodUnknown.java", getMessage())
  }

  fun `test ParameterMethodTainted`() {
    prepareCheckFramework()
    myFixture.testQuickFix("ParameterMethodTainted.java", getMessage())
  }

  fun `test ParameterFieldUnknown`() {
    prepareCheckFramework()
    myFixture.testQuickFix("ParameterFieldUnknown.java", getMessage())
  }

  fun `test ParameterFieldTainted`() {
    prepareCheckFramework()
    myFixture.testQuickFix("ParameterFieldTainted.java", getMessage())
  }

  fun `test Parameter`() {
    prepareCheckFramework()
    myFixture.testQuickFix("Parameter.java", getMessage())
  }

  fun `test MethodMethodUnknown`() {
    prepareCheckFramework()
    myFixture.testQuickFix("MethodMethodUnknown.java", getMessage())
  }

  fun `test MethodMethodTainted`() {
    prepareCheckFramework()
    myFixture.testQuickFix("MethodMethodTainted.java", getMessage())
  }

  fun `test MethodFieldUnknown`() {
    prepareCheckFramework()
    myFixture.testQuickFix("MethodFieldUnknown.java", getMessage())
  }

  fun `test MethodFieldTainted`() {
    prepareCheckFramework()
    myFixture.testQuickFix("MethodFieldTainted.java", getMessage())
  }

  fun `test MethodFieldMethodUnknown`() {
    prepareCheckFramework()
    myFixture.testQuickFix("MethodFieldMethodUnknown.java", getMessage())
  }

  fun `test MethodFieldMethodTainted`() {
    prepareCheckFramework()
    myFixture.testQuickFix("MethodFieldMethodTainted.java", getMessage())
  }

  fun `test AnotherClassMethodCall`() {
    prepareCheckFramework()
    myFixture.testQuickFix("AnotherClassMethodCall.java", getMessage())
  }
}
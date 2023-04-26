package com.intellij.codeInspection.tests.java.sourceToSink

import com.intellij.codeInspection.tests.sourceToSink.SourceToSinkFlowInspectionTestBase
import com.intellij.jvm.analysis.JavaJvmAnalysisTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.TestDataPath

private const val inspectionPath = "/codeInspection/sourceToSinkFlow/propagateSafe"

@TestDataPath("\$CONTENT_ROOT/testData$inspectionPath")
class JavaPropagateFixSourceToSinkFlowInspectionTest : SourceToSinkFlowInspectionTestBase() {

  override fun getProjectDescriptor(): LightProjectDescriptor {
    return JAVA_17
  }

  override fun getBasePath(): String {
    return JavaJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + inspectionPath
  }

  fun `test ParameterMethodUntainted`() {
    prepareCheckFramework()
    myFixture.testQuickFix("ParameterMethodUntainted.java", "Show propagation tree from 'a'")
  }

  fun `test tainted field`() {
    prepareCheckFramework()
    myFixture.testQuickFixUnavailable("TaintedField.java", "Show propagation tree from 'a'")
  }

  fun `test ParameterParameterUntainted`() {
    prepareCheckFramework()
    myFixture.testQuickFix("ParameterParameterUntainted.java", "Show propagation tree from 'a'")
  }

  fun `test ParameterMethodUnknown`() {
    prepareCheckFramework()
    myFixture.testQuickFix("ParameterMethodUnknown.java", "Show propagation tree from 's'")
  }

  fun `test ParameterMethodTainted`() {
    prepareCheckFramework()
    myFixture.testQuickFix("ParameterMethodTainted.java", "Show propagation tree from 's'")
  }

  fun `test ParameterFieldUnknown`() {
    prepareCheckFramework()
    myFixture.testQuickFix("ParameterFieldUnknown.java", "Show propagation tree from 's'")
  }

  fun `test ParameterFieldTainted`() {
    prepareCheckFramework()
    myFixture.testQuickFix("ParameterFieldTainted.java", "Show propagation tree from 's'")
  }

  fun `test Parameter`() {
    prepareCheckFramework()
    myFixture.testQuickFix("Parameter.java", "Show propagation tree from 's'")
  }

  fun `test MethodMethodUnknown`() {
    prepareCheckFramework()
    myFixture.testQuickFix("MethodMethodUnknown.java", "Show propagation tree from 's'")
  }

  fun `test MethodMethodTainted`() {
    prepareCheckFramework()
    myFixture.testQuickFix("MethodMethodTainted.java", "Show propagation tree from 's'")
  }

  fun `test MethodFieldUnknown`() {
    prepareCheckFramework()
    myFixture.testQuickFix("MethodFieldUnknown.java", "Show propagation tree from 's'")
  }

  fun `test MethodFieldTainted`() {
    prepareCheckFramework()
    myFixture.testQuickFix("MethodFieldTainted.java", "Show propagation tree from 's'")
  }

  fun `test MethodFieldMethodUnknown`() {
    prepareCheckFramework()
    myFixture.testQuickFix("MethodFieldMethodUnknown.java", "Show propagation tree from 's'")
  }

  fun `test MethodFieldMethodTainted`() {
    prepareCheckFramework()
    myFixture.testQuickFix("MethodFieldMethodTainted.java", "Show propagation tree from 's'")
  }

  fun `test AnotherClassMethodCall`() {
    prepareCheckFramework()
    myFixture.testQuickFix("AnotherClassMethodCall.java", "Show propagation tree from 's'")
  }
}
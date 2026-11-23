package com.intellij.codeInspection.tests.java.sourceToSink

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.jvm.analysis.JavaJvmAnalysisTestUtil
import com.intellij.jvm.analysis.internal.testFramework.sourceToSink.sourceToSinkFlowInspectionFixture
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

private const val INSPECTION_PATH = "/codeInspection/sourceToSinkFlow/propagateSafe"

@TestDataPath("\$CONTENT_ROOT/testData$INSPECTION_PATH")
@TestApplication
class JavaPropagateFixSourceToSinkFlowInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_17)
  }

  private val myFixture by sourceToSinkFlowInspectionFixture(projectFixture, JavaJvmAnalysisTestUtil.testDataPath + INSPECTION_PATH)

  private fun getMessage() = JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.propagate.safe.text")

  @Test
  fun `test ParameterMethodUntainted`() {
    myFixture.prepareCheckFramework()
    myFixture.testQuickFix("ParameterMethodUntainted.java", getMessage())
  }

  @Test
  fun `test tainted field`() {
    myFixture.prepareCheckFramework()
    myFixture.testQuickFixUnavailable("TaintedField.java", getMessage())
  }

  @Test
  fun `test ParameterParameterUntainted`() {
    myFixture.prepareCheckFramework()
    myFixture.testQuickFix("ParameterParameterUntainted.java", getMessage())
  }

  @Test
  fun `test ParameterMethodUnknown`() {
    myFixture.prepareCheckFramework()
    myFixture.testQuickFix("ParameterMethodUnknown.java", getMessage())
  }

  @Test
  fun `test ParameterMethodTainted`() {
    myFixture.prepareCheckFramework()
    myFixture.testQuickFix("ParameterMethodTainted.java", getMessage())
  }

  @Test
  fun `test ParameterFieldUnknown`() {
    myFixture.prepareCheckFramework()
    myFixture.testQuickFix("ParameterFieldUnknown.java", getMessage())
  }

  @Test
  fun `test ParameterFieldTainted`() {
    myFixture.prepareCheckFramework()
    myFixture.testQuickFix("ParameterFieldTainted.java", getMessage())
  }

  @Test
  fun `test Parameter`() {
    myFixture.prepareCheckFramework()
    myFixture.testQuickFix("Parameter.java", getMessage())
  }

  @Test
  fun `test MethodMethodUnknown`() {
    myFixture.prepareCheckFramework()
    myFixture.testQuickFix("MethodMethodUnknown.java", getMessage())
  }

  @Test
  fun `test MethodMethodTainted`() {
    myFixture.prepareCheckFramework()
    myFixture.testQuickFix("MethodMethodTainted.java", getMessage())
  }

  @Test
  fun `test MethodFieldUnknown`() {
    myFixture.prepareCheckFramework()
    myFixture.testQuickFix("MethodFieldUnknown.java", getMessage())
  }

  @Test
  fun `test MethodFieldTainted`() {
    myFixture.prepareCheckFramework()
    myFixture.testQuickFix("MethodFieldTainted.java", getMessage())
  }

  @Test
  fun `test MethodFieldMethodUnknown`() {
    myFixture.prepareCheckFramework()
    myFixture.testQuickFix("MethodFieldMethodUnknown.java", getMessage())
  }

  @Test
  fun `test MethodFieldMethodTainted`() {
    myFixture.prepareCheckFramework()
    myFixture.testQuickFix("MethodFieldMethodTainted.java", getMessage())
  }

  @Test
  fun `test AnotherClassMethodCall`() {
    myFixture.prepareCheckFramework()
    myFixture.testQuickFix("AnotherClassMethodCall.java", getMessage())
  }
}
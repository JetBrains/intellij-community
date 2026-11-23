package com.intellij.codeInspection.tests.java.sourceToSink

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.jvm.analysis.JavaJvmAnalysisTestUtil
import com.intellij.jvm.analysis.internal.testFramework.sourceToSink.sourceToSinkFlowInspectionFixture
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

private const val INSPECTION_PATH = "/codeInspection/sourceToSinkFlow/markAsSafeFix"

@TestDataPath("\$CONTENT_ROOT/testData$INSPECTION_PATH")
@TestApplication
class JavaMarkAsSafeFixSourceToSinkFlowInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_17)
  }

  private val myFixture by sourceToSinkFlowInspectionFixture(projectFixture, JavaJvmAnalysisTestUtil.testDataPath + INSPECTION_PATH)

  private fun getMessage() = JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.mark.as.safe.text")

  @Test
  fun `test unsafe var`() {
    myFixture.prepareCheckFramework()
    myFixture.testQuickFix("UnsafeVar.java", getMessage(), true)
  }
  @Test
  fun `test unsafe method call`() {
    myFixture.prepareCheckFramework()
    myFixture.testQuickFixUnavailable("UnsafeMethodCall.java", getMessage())
  }
  @Test
  fun `test simple`() {
    myFixture.prepareCheckFramework()
    myFixture.testQuickFix("Simple.java", getMessage(), true)
  }

  @Test
  fun `test safe var`() {
    myFixture.prepareCheckFramework()
    myFixture.testQuickFixUnavailable("SafeVar.java", getMessage())
  }

  @Test
  fun `test multiple methods`() {
    myFixture.prepareCheckFramework()
    myFixture.testQuickFix("MultipleMethods.java", getMessage(), true)
  }

  @Test
  fun `test method calls`() {
    myFixture.prepareCheckFramework()
    myFixture.testQuickFix("MethodCall.java", getMessage(), true)
  }

  @Test
  fun `test fields`() {
    myFixture.prepareCheckFramework()
    myFixture.testQuickFix("Field.java", getMessage(), true)
  }

  @Test
  fun `test chained vars`() {
    myFixture.prepareCheckFramework()
    myFixture.testQuickFix("ChainedVars.java", getMessage(), true)
  }

  @Test
  fun `test alias var`() {
    myFixture.prepareCheckFramework()
    myFixture.testQuickFix("AliasVar.java", getMessage(), true)
  }

  @Test
  fun `test records`() {
    myFixture.prepareCheckFramework()
    myFixture.testQuickFix("Records.java", getMessage(), true)
  }

  @Test
  fun `test common cases with checkFramework`() {
    myFixture.prepareCheckFramework()
    myFixture.testQuickFix("CommonCasesCheckFramework.java", getMessage(), true)
  }

  @Test
  fun `test common cases with jsr`() {
    myFixture.prepareJsr()
    myFixture.testQuickFix("CommonCasesJsr.java", getMessage(), true)
  }

  @Test
  fun `test unknown field`() {
    myFixture.prepareCheckFramework()
    myFixture.testQuickFix("UnknownField.java", getMessage(), true)
  }

  @Test
  fun `test unknown method`() {
    myFixture.prepareCheckFramework()
    myFixture.testQuickFix("UnknownMethod.java", getMessage(), true)
  }

  @Test
  fun `test tainted method`() {
    myFixture.prepareCheckFramework()
    myFixture.testQuickFixUnavailable("TaintedMethod.java", getMessage())
  }

  @Test
  fun `test recursive 2 path`() {
    myFixture.prepareCheckFramework()
    myFixture.testQuickFix("RecursiveTwoPaths.java", getMessage(), true)
  }
  @Test
  fun `test recursive`() {
    myFixture.prepareCheckFramework()
    myFixture.testQuickFix("Recursive.java", getMessage(), true)
  }
}

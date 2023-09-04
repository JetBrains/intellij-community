package com.intellij.codeInspection.tests.java.sourceToSink

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInspection.tests.sourceToSink.SourceToSinkFlowInspectionTestBase
import com.intellij.jvm.analysis.JavaJvmAnalysisTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.TestDataPath

private const val INSPECTION_PATH = "/codeInspection/sourceToSinkFlow/markAsSafeFix"

@TestDataPath("\$CONTENT_ROOT/testData$INSPECTION_PATH")
class JavaMarkAsSafeFixSourceToSinkFlowInspectionTest : SourceToSinkFlowInspectionTestBase() {

  override fun getProjectDescriptor(): LightProjectDescriptor {
    return JAVA_17
  }

  override fun getBasePath(): String {
    return JavaJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + INSPECTION_PATH
  }

  private fun getMessage() = JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.mark.as.safe.text")

  fun `test unsafe var`() {
    prepareCheckFramework()
    myFixture.testQuickFix("UnsafeVar.java", getMessage(), true)
  }
  fun `test unsafe method call`() {
    prepareCheckFramework()
    myFixture.testQuickFixUnavailable("UnsafeMethodCall.java", getMessage())
  }
  fun `test simple`() {
    prepareCheckFramework()
    myFixture.testQuickFix("Simple.java", getMessage(), true)
  }

  fun `test safe var`() {
    prepareCheckFramework()
    myFixture.testQuickFixUnavailable("SafeVar.java", getMessage())
  }

  fun `test multiple methods`() {
    prepareCheckFramework()
    myFixture.testQuickFix("MultipleMethods.java", getMessage(), true)
  }

  fun `test method calls`() {
    prepareCheckFramework()
    myFixture.testQuickFix("MethodCall.java", getMessage(), true)
  }

  fun `test fields`() {
    prepareCheckFramework()
    myFixture.testQuickFix("Field.java", getMessage(), true)
  }

  fun `test chained vars`() {
    prepareCheckFramework()
    myFixture.testQuickFix("ChainedVars.java", getMessage(), true)
  }

  fun `test alias var`() {
    prepareCheckFramework()
    myFixture.testQuickFix("AliasVar.java", getMessage(), true)
  }

  fun `test records`() {
    prepareCheckFramework()
    myFixture.testQuickFix("Records.java", getMessage(), true)
  }

  fun `test common cases with checkFramework`() {
    prepareCheckFramework()
    myFixture.testQuickFix("CommonCasesCheckFramework.java", getMessage(), true)
  }

  fun `test common cases with jsr`() {
    prepareJsr()
    myFixture.testQuickFix("CommonCasesJsr.java", getMessage(), true)
  }

  fun `test unknown field`() {
    prepareCheckFramework()
    myFixture.testQuickFix("UnknownField.java", getMessage(), true)
  }

  fun `test unknown method`() {
    prepareCheckFramework()
    myFixture.testQuickFix("UnknownMethod.java", getMessage(), true)
  }

  fun `test tainted method`() {
    prepareCheckFramework()
    myFixture.testQuickFixUnavailable("TaintedMethod.java", getMessage())
  }

  fun `test recursive 2 path`() {
    prepareCheckFramework()
    myFixture.testQuickFix("RecursiveTwoPaths.java", getMessage(), true)
  }
  fun `test recursive`() {
    prepareCheckFramework()
    myFixture.testQuickFix("Recursive.java", getMessage(), true)
  }
}

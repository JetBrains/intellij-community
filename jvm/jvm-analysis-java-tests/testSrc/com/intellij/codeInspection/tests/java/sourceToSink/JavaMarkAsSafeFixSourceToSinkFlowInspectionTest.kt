package com.intellij.codeInspection.tests.java.sourceToSink

import com.intellij.codeInspection.tests.sourceToSink.SourceToSinkFlowInspectionTestBase
import com.intellij.jvm.analysis.JavaJvmAnalysisTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.TestDataPath

private const val inspectionPath = "/codeInspection/sourceToSinkFlow/markAsSafeFix"

@TestDataPath("\$CONTENT_ROOT/testData$inspectionPath")
class JavaMarkAsSafeFixSourceToSinkFlowInspectionTest : SourceToSinkFlowInspectionTestBase() {

  override fun getProjectDescriptor(): LightProjectDescriptor {
    return JAVA_17
  }

  override fun getBasePath(): String {
    return JavaJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + inspectionPath
  }
  fun `test unsafe var`() {
    prepareCheckFramework()
    myFixture.testQuickFixUnavailable("UnsafeVar.java", "Mark 's2' as requiring validation")
  }
  fun `test unsafe method call`() {
    prepareCheckFramework()
    myFixture.testQuickFixUnavailable("UnsafeMethodCall.java", "Mark 'source' as requiring validation")
  }
  fun `test simple`() {
    prepareCheckFramework()
    myFixture.testQuickFix("Simple.java", "Mark 's' as requiring validation", true)
  }

  fun `test safe var`() {
    prepareCheckFramework()
    myFixture.testQuickFixUnavailable("SafeVar.java", "Mark 's' as requiring validation")
  }

  fun `test multiple methods`() {
    prepareCheckFramework()
    myFixture.testQuickFix("MultipleMethods.java", "Mark 's2' as requiring validation", true)
  }

  fun `test method calls`() {
    prepareCheckFramework()
    myFixture.testQuickFix("MethodCall.java", "Mark 'foo' as requiring validation", true)
  }

  fun `test fields`() {
    prepareCheckFramework()
    myFixture.testQuickFix("Field.java", "Mark 's2' as requiring validation", true)
  }

  fun `test chained vars`() {
    prepareCheckFramework()
    myFixture.testQuickFix("ChainedVars.java", "Mark 's2' as requiring validation", true)
  }

  fun `test alias var`() {
    prepareCheckFramework()
    myFixture.testQuickFix("AliasVar.java", "Mark 'alias' as requiring validation", true)
  }

  fun `test records`() {
    prepareCheckFramework()
    myFixture.testQuickFix("Records.java", "Mark 's' as requiring validation", true)
  }

  fun `test common cases with checkFramework`() {
    prepareCheckFramework()
    myFixture.testQuickFix("CommonCasesCheckFramework.java", "Mark 's1' as requiring validation", true)
  }

  fun `test common cases with jsr`() {
    prepareJsr()
    myFixture.testQuickFix("CommonCasesJsr.java", "Mark 's1' as requiring validation", true)
  }
}

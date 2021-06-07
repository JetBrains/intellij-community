package com.intellij.codeInspection;

import com.intellij.execution.junit.codeInsight.JUnit5TestFrameworkSetupUtil;
import com.intellij.jvm.analysis.JvmAnalysisKtTestsUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JUnit5MalformedParameterizedTest extends LightJavaInspectionTestCase {
  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new JUnit5MalformedParameterizedInspection();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    JUnit5TestFrameworkSetupUtil.setupJUnit5Library(myFixture);
  }

  public void testMalformedSources() { doTest(); }
  public void testMethodSource() { doTest(); }
  public void testEnumSource() { doTest(); }
  public void testMalformedSourcesImplicitConversion() { doTest(); }
  public void testMalformedSourcesImplicitParameters() { doTest(); }
  public void testMalformedSourcesTestInstancePerClass() { doTest(); }

  @Override
  protected String getBasePath() {
    return JvmAnalysisKtTestsUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + "/codeInspection/junit5malformed";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }
}
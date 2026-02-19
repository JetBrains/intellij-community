package com.intellij.codeInspection.tests.java;

import com.intellij.jvm.analysis.JavaJvmAnalysisTestUtil;
import com.intellij.jvm.analysis.internal.testFramework.StringToUpperWithoutLocaleInspectionTestBase;
import com.intellij.testFramework.TestDataPath;

@TestDataPath("$CONTENT_ROOT/testData/codeInspection/toUpperWithoutLocale")
public class JavaStringToUpperWithoutLocaleInspectionTest extends StringToUpperWithoutLocaleInspectionTestBase {

  @Override
  protected String getBasePath() {
    return JavaJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + "/codeInspection/toUpperWithoutLocale";
  }

  public void testSimpleCases() {
    myFixture.testHighlighting("SimpleCases.java");
    //TODO test after quickfix once it's implemented
  }

  public void testNonNlsCases() {
    //TODO test #4 (about equals())
    //TODO test #5 (about @NonNls in the left side of assignment expression)
    //TODO test #6 with assigning method return value to variable? If this is not super hard to support.
    myFixture.testHighlighting("NonNlsCases.java");
  }

  //TODO test pkg/subPkg
}

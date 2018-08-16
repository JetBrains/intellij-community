package com.intellij.codeInspection;

import com.intellij.jvm.analysis.JvmAnalysisTestsUtil;
import com.intellij.testFramework.TestDataPath;

@TestDataPath("$CONTENT_ROOT/testData/codeInspection/toUpperWithoutLocale")
public class StringToUpperWithoutLocaleInspectionTest extends StringToUpperWithoutLocaleInspectionTestBase {
  @Override
  protected String getBasePath() {
    return JvmAnalysisTestsUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + "/codeInspection/toUpperWithoutLocale";
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

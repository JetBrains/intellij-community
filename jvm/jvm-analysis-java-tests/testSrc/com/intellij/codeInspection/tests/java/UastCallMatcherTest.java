package com.intellij.codeInspection.tests.java;

import com.intellij.codeInspection.tests.UastCallMatcherTestBase;
import com.intellij.jvm.analysis.JavaJvmAnalysisTestUtil;
import com.intellij.testFramework.TestDataPath;

@TestDataPath("$CONTENT_ROOT/testData/codeInspection/uastCallMatcher")
public class UastCallMatcherTest extends UastCallMatcherTestBase {
  @Override
  protected String getBasePath() {
    return JavaJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + "/codeInspection/uastCallMatcher";
  }

  public void testCallExpressions() {
    doTestCallExpressions("MyClass.java");
  }

  public void testCallableReferences() {
    doTestCallableReferences("MethodReferences.java");
  }
}

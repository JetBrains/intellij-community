package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.miscGenerics.SuspiciousCollectionsMethodCallsInspection;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

public class SuspiciousCollectionMethodCallsTest extends LightCodeInsightFixtureTestCase {
  private final SuspiciousCollectionsMethodCallsInspection myTool = new SuspiciousCollectionsMethodCallsInspection();

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/inspection/suspiciousCalls";
  }

  private void doTest() throws Exception {
    myFixture.enableInspections(myTool);
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testUseDfa() throws Exception { doTest(); }
  public void testWildcard() throws Exception { doTest(); }
  public void testIgnoreConvertible() throws Exception {
    myTool.REPORT_CONVERTIBLE_METHOD_CALLS = false;
    doTest();
  }
}

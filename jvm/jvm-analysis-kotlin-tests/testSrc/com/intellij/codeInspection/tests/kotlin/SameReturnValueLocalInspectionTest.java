package com.intellij.codeInspection.tests.kotlin;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.sameReturnValue.SameReturnValueInspection;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class SameReturnValueLocalInspectionTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/sameReturnValue/";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new SameReturnValueInspection().getSharedLocalInspectionTool());
  }

  public void testJava() {
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testKotlin() {
    myFixture.testHighlighting(getTestName(false) + ".kt");
  }
}

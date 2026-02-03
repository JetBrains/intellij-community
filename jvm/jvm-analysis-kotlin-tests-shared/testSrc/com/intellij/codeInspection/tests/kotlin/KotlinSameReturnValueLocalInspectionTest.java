package com.intellij.codeInspection.tests.kotlin;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.sameReturnValue.SameReturnValueInspection;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider;
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProviderKt;

public abstract class KotlinSameReturnValueLocalInspectionTest extends LightJavaCodeInsightFixtureTestCase implements ExpectedPluginModeProvider {

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/sameReturnValue/";
  }

  @Override
  protected void setUp() throws Exception {
    ExpectedPluginModeProviderKt.setUpWithKotlinPlugin(this, getTestRootDisposable(), super::setUp);
    myFixture.enableInspections(new SameReturnValueInspection().getSharedLocalInspectionTool());
  }

  public void testJava() {
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testKotlin() {
    myFixture.testHighlighting(getTestName(false) + ".kt");
  }
}

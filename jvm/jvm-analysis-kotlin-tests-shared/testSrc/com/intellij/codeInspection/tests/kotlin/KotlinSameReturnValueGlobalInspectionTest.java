package com.intellij.codeInspection.tests.kotlin;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.sameReturnValue.SameReturnValueInspection;
import com.intellij.testFramework.JavaInspectionTestCase;
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider;
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProviderKt;

public abstract class KotlinSameReturnValueGlobalInspectionTest extends JavaInspectionTestCase implements ExpectedPluginModeProvider {
  private final SameReturnValueInspection myGlobalTool = new SameReturnValueInspection();

  @Override
  protected void setUp() throws Exception {
    ExpectedPluginModeProviderKt.setUpWithKotlinPlugin(this, getTestRootDisposable(), super::setUp);
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/jvm";
  }

  private String getGlobalTestDir() {
    return "sameReturnValue/" + getTestName(true);
  }

  public void testJava() {
    doTest(getGlobalTestDir(), myGlobalTool);
  }

  public void testKotlin() {
    doTest(getGlobalTestDir(), myGlobalTool);
  }

  public void testMixed() {
    doTest(getGlobalTestDir(), myGlobalTool);
  }
}

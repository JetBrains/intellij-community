package com.intellij.codeInspection.tests.kotlin;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.unusedReturnValue.UnusedReturnValue;
import com.intellij.testFramework.JavaInspectionTestCase;
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider;
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProviderKt;

public abstract class KotlinUnusedReturnValueInspectionKtTest extends JavaInspectionTestCase implements ExpectedPluginModeProvider {
  private final UnusedReturnValue myGlobalTool = new UnusedReturnValue();

  @Override
  protected void setUp() throws Exception {
    ExpectedPluginModeProviderKt.setUpWithKotlinPlugin(this, getTestRootDisposable(), super::setUp);
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/jvm";
  }

  private String getGlobalTestDir() {
    return "unusedReturnValue/" + getTestName(true);
  }

  public void testUsedPropertyBySimpleName() {
    doTest(getGlobalTestDir(), myGlobalTool);
  }

}

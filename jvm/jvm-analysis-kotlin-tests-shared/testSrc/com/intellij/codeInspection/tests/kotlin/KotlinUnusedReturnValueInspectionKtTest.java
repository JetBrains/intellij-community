package com.intellij.codeInspection.tests.kotlin;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.unusedReturnValue.UnusedReturnValue;
import com.intellij.testFramework.JavaInspectionTestCase;
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider;

public abstract class KotlinUnusedReturnValueInspectionKtTest extends JavaInspectionTestCase implements ExpectedPluginModeProvider {
  private final UnusedReturnValue myGlobalTool = new UnusedReturnValue();

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

package com.intellij.codeInsight;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author anna
 */
public class ConvertToThreadLocalIntentionTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected String getBasePath() {
    return "/intentions/threadLocal";
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/java/typeMigration/testData";
  }

  public void test() {
    doAllTests();
  }
}
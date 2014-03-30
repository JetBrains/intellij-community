package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.pom.java.LanguageLevel;

/**
 * @author ven
 */
public class GenericCreateFromUsageTest extends LightQuickFixParameterizedTestCase {

  public void test() throws Exception { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/genericCreateFromUsage";
  }

  @Override
  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_1_5;
  }
}

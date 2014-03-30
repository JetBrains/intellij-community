package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.pom.java.LanguageLevel;

public class ModifierTest extends LightQuickFixParameterizedTestCase {
  public void test() throws Exception { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/modifier";
  }

  @Override
  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_1_7;
  }
}

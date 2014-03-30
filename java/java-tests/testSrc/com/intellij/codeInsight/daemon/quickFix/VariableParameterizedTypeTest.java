package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.pom.java.LanguageLevel;

public class VariableParameterizedTypeTest extends LightQuickFixParameterizedTestCase {

  public void test() throws Exception { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/variableParameterizedType";
  }

  @Override
  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_1_5;
  }
}


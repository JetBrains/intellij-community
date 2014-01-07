package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.pom.java.LanguageLevel;

public class ChangeNewOperatorTypeTest extends LightQuickFixParameterizedTestCase {

  @Override
  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_1_7;
  }

  public void test() throws Exception {
    doAllTests(); 
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/changeNewOperatorType";
  }

}

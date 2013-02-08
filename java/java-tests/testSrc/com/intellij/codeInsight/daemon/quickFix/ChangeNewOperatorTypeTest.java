package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.pom.java.LanguageLevel;

public class ChangeNewOperatorTypeTest extends LightQuickFixTestCase {

  public void test() throws Exception {
    setLanguageLevel(LanguageLevel.JDK_1_7);
    doAllTests(); 
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/changeNewOperatorType";
  }

}

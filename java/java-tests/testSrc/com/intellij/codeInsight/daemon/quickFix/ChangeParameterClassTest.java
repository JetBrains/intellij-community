
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.pom.java.LanguageLevel;



public class ChangeParameterClassTest extends LightQuickFix15TestCase {
  @Override
  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_1_5;
  }

  public void test() throws Exception {
    doAllTests();
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/changeParameterClass";
  }

}


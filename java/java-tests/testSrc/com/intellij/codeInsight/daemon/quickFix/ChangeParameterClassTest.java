
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.pom.java.LanguageLevel;



public class ChangeParameterClassTest extends LightQuickFix15TestCase {

  public void test() throws Exception {
    setLanguageLevel(LanguageLevel.JDK_1_5);
    doAllTests();
  }

  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/changeParameterClass";
  }

}


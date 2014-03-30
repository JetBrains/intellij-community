package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;

public class CastMethodParametersTest extends LightQuickFixParameterizedTestCase {

  @Override
  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_1_3;
  }

  public void test() throws Exception { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/castMethodParameters";
  }
}


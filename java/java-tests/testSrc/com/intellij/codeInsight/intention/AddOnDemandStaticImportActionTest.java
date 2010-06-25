package com.intellij.codeInsight.intention;

import com.intellij.codeInsight.daemon.LightIntentionActionTestCase;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;

public class AddOnDemandStaticImportActionTest extends LightIntentionActionTestCase {
  private LanguageLevel myLanguageLevel;

  public void test() throws Exception { doAllTests(); }

  protected void setUp() throws Exception {
    super.setUp();
    myLanguageLevel = LanguageLevelProjectExtension.getInstance(getProject()).getLanguageLevel();
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_7);
  }

  protected void tearDown() throws Exception {
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(myLanguageLevel);
    super.tearDown();
  }

  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/addOnDemandStaticImport";
  }

  protected Sdk getProjectJDK() {
    return JavaSdkImpl.getMockJdk17();
  }
}

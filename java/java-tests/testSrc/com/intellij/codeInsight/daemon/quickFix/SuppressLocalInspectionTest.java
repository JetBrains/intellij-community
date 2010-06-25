
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.localCanBeFinal.LocalCanBeFinal;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;


public class SuppressLocalInspectionTest extends LightQuickFixTestCase {

  protected void setUp() throws Exception {
    super.setUp();
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_3);
  }

  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new LocalCanBeFinal()};
  }

  protected void tearDown() throws Exception {
    super.tearDown();
  }

  public void test() throws Exception { doAllTests(); }

  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/suppressLocalInspection";
  }

}


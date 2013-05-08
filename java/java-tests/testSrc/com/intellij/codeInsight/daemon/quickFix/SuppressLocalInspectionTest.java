
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.localCanBeFinal.LocalCanBeFinal;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;

public class SuppressLocalInspectionTest extends LightQuickFixTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_3);
  }

  @NotNull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new LocalCanBeFinal()};
  }

  public void test() throws Exception { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/suppressLocalInspection";
  }

}


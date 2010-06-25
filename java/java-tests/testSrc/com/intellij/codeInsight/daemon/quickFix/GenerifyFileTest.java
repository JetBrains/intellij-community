package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;


public class GenerifyFileTest extends LightQuickFixAvailabilityTestCase {
  private LanguageLevel oldLanguageLevel;


  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[] {new UncheckedWarningLocalInspection()};
  }

  public void test() throws Exception { doAllTests(); }

  protected void setUp() throws Exception {
    super.setUp();
    oldLanguageLevel = LanguageLevelProjectExtension.getInstance(getProject()).getLanguageLevel();
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
  }

  protected void tearDown() throws Exception {
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(oldLanguageLevel);
    super.tearDown();
  }

  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/generifyFile";
  }
}


package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;


public class GenerifyFileTest extends LightQuickFixAvailabilityTestCase {

  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[] {new UncheckedWarningLocalInspection()};
  }

  public void test() throws Exception { doAllTests(); }

  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/generifyFile";
  }
}


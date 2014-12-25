package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;

public class RemoveUnusedVariableTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableInspectionTool(new UnusedDeclarationInspection());
  }
  

  public void test() throws Exception {
    doAllTests();
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/removeUnusedVariable";
  }

}


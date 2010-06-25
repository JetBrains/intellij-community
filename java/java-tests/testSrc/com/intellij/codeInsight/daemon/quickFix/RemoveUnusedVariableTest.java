
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspection;

public class RemoveUnusedVariableTest extends LightQuickFixTestCase {
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{ new UnusedSymbolLocalInspection()};
  }
  

  public void test() throws Exception {
    doAllTests();
  }

  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/removeUnusedVariable";
  }

}


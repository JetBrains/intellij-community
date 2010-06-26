
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInspection.unneededThrows.RedundantThrowsDeclaration;


public class MethodThrowsTest extends LightQuickFixTestCase {

  public void test() throws Exception { enableInspectionTool(new RedundantThrowsDeclaration()); doAllTests(); }

  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/methodThrows";
  }

}


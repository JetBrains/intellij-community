
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInspection.accessStaticViaInstance.AccessStaticViaInstance;


public class AccessStaticViaInstanceTest extends LightQuickFixTestCase {

  public void test() throws Exception { enableInspectionTool(new AccessStaticViaInstance()); doAllTests(); }

  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/accessStaticViaInstance";
  }

}


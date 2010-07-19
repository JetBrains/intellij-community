package com.intellij.codeInsight.intention;

import com.intellij.codeInsight.daemon.LightIntentionActionTestCase;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;

public class AddOnDemandStaticImportActionTest extends LightIntentionActionTestCase {

  public void test() throws Exception { doAllTests(); }

  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/addOnDemandStaticImport";
  }

  protected Sdk getProjectJDK() {
    return JavaSdkImpl.getMockJdk17();
  }
}

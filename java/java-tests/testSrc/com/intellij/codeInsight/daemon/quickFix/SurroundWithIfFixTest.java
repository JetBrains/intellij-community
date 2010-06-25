/*
 * User: anna
 * Date: 21-Mar-2008
 */
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.dataFlow.DataFlowInspection;

public class SurroundWithIfFixTest extends LightQuickFixTestCase {
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new DataFlowInspection()};
  }

  public void test() throws Exception {
     doAllTests();
   }

  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/surroundWithIf";
  }

  @Override
  protected Sdk getProjectJDK() {
    return JavaSdkImpl.getMockJdk15("java 1.5");
  }
}
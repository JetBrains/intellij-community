/*
 * User: anna
 * Date: 17-Jun-2007
 */
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.dataFlow.DataFlowInspection;
import org.jetbrains.annotations.NonNls;

public class FixAllQuickfixTest extends LightQuickFixTestCase {
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[] {
      new DataFlowInspection()
    };
  }

  public void test() throws Exception { doAllTests(); }

  @NonNls
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/fixAll";
  }
}
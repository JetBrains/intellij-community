package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.dataFlow.DataFlowInspection;
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper;
import com.intellij.codeInspection.visibility.VisibilityInspection;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.testFramework.IdeaTestUtil;
import com.siyeh.ig.style.UnnecessaryFullyQualifiedNameInspection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class FixAllQuickfixTest extends LightQuickFixParameterizedTestCase {
  @NotNull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[] {
      new DataFlowInspection(),
      new UnnecessaryFullyQualifiedNameInspection()
    };
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableInspectionTool(new GlobalInspectionToolWrapper(new VisibilityInspection()));
  }

  public void test() throws Exception { doAllTests(); }

  @Override
  @NonNls
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/fixAll";
  }

  @Override
  protected Sdk getProjectJDK() {
    // jdk 1.7 needed because it contains java.sql.Date for FullyQualifiedName test
    return IdeaTestUtil.getMockJdk17();
  }
}
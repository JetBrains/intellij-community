package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspection;
import org.jetbrains.annotations.NotNull;

public class InitializeFinalFieldInConstructorFixTest extends LightQuickFixTestCase {
  @NotNull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{ new UnusedSymbolLocalInspection()};
  }

  public void test() throws Exception { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/initializeFinalFieldInConstructor";
  }
}
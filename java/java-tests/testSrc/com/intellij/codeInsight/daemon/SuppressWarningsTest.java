/*
 * User: anna
 * Date: 03-Sep-2007
 */
package com.intellij.codeInsight.daemon;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspection;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import org.jetbrains.annotations.NonNls;

public class SuppressWarningsTest extends LightDaemonAnalyzerTestCase {
  @NonNls private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/advHighlighting";

  private void doTest(boolean checkWarnings) throws Exception {
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", checkWarnings, false);
  }

  @Override protected Sdk getProjectJDK() {
    return JavaSdkImpl.getMockJdk17("java 1.5");
  }

  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new UnusedSymbolLocalInspection()};
  }

  public void testSuppressed() throws Exception {
    doTest(true);
  }
}

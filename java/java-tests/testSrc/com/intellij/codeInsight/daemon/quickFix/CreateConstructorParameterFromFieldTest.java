package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.siyeh.ig.style.MissortedModifiersInspection;
import com.siyeh.ig.style.UnqualifiedFieldAccessInspection;

/**
 * @author cdr
 */
public class CreateConstructorParameterFromFieldTest extends LightQuickFixParameterizedTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableInspectionTools(new UnusedDeclarationInspection(), new MissortedModifiersInspection(), new UnqualifiedFieldAccessInspection());
  }

  public void test() throws Exception { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/createConstructorParameterFromField";
  }
}

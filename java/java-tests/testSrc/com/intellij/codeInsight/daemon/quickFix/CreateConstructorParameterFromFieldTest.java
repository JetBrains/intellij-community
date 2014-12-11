package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.siyeh.ig.style.MissortedModifiersInspection;
import com.siyeh.ig.style.UnqualifiedFieldAccessInspection;

/**
 * @author cdr
 */
public class CreateConstructorParameterFromFieldTest extends LightQuickFixParameterizedTestCase {

  private boolean myPreferLongNames;
  
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableInspectionTools(new UnusedDeclarationInspection(), new MissortedModifiersInspection(), new UnqualifiedFieldAccessInspection());
    final CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
    myPreferLongNames = settings.PREFER_LONGER_NAMES;
    if (getTestName(false).contains("SameParameter")) {
      settings.PREFER_LONGER_NAMES = false;
    }
  }

  @Override
  protected void tearDown() throws Exception {
    CodeStyleSettingsManager.getSettings(getProject()).PREFER_LONGER_NAMES = myPreferLongNames;
    super.tearDown();
  }

  public void test() throws Exception { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/createConstructorParameterFromField";
  }
}

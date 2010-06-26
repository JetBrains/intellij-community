package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.psi.codeStyle.CodeStyleSettingsManager;

/**
 * @author ven
 */
public class CreateClassFromNewTest extends LightQuickFixTestCase {

  public void test() throws Exception {
    CodeStyleSettingsManager.getSettings(getProject()).SPACE_BEFORE_CLASS_LBRACE = true;
    doAllTests(); 
  }

  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/createClassFromNew";
  }
}

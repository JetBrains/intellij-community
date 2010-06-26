package com.intellij.codeInsight.intention;

import com.intellij.codeInsight.daemon.LightIntentionActionTestCase;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;

/**
 * @author yole
 */
public class CopyAbstractMethodImplementationTest extends LightIntentionActionTestCase {
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/copyAbstractMethodImplementation";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject()).clone();
    settings.INSERT_OVERRIDE_ANNOTATION = false;
    CodeStyleSettingsManager.getInstance(getProject()).setTemporarySettings(settings);
  }

  @Override
  protected void tearDown() throws Exception {
    CodeStyleSettingsManager.getInstance(getProject()).dropTemporarySettings();
    super.tearDown();
  }

  public void test() throws Exception {
    doAllTests();
  }
}

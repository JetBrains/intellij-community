package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.LightIntentionActionTestCase;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;

/**
 * @author ven
 */
public class CreateFieldFromParameterTest extends LightIntentionActionTestCase {
  protected void setUp() throws Exception {
    super.setUp();
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
    settings.FIELD_NAME_PREFIX = "my";
  }

  protected void tearDown() throws Exception {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
    settings.FIELD_NAME_PREFIX = "";
    super.tearDown();
  }

  public void test() throws Exception { doAllTests(); }

  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/createFieldFromParameter";
  }

  @Override protected Sdk getProjectJDK() {
    return JavaSdkImpl.getMockJdk15("java 1.5");
  }
}

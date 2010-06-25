package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.codeInsight.daemon.LightIntentionActionTestCase;
import org.jdom.Element;

/**
 * @author ven
 */
public class AssignFieldFromParameterTest extends LightIntentionActionTestCase {
  private Element myOldSettings;

  protected void setUp() throws Exception {
    super.setUp();
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
    myOldSettings = new Element("dummy2");
    settings.writeExternal(myOldSettings);
    settings.FIELD_NAME_PREFIX = "my";
    settings.STATIC_FIELD_NAME_PREFIX = "our";
  }

  protected void tearDown() throws Exception {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
    settings.readExternal(myOldSettings);
    super.tearDown();
  }

  public void test() throws Exception { doAllTests(); }

  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/assignFieldFromParameter";
  }

}

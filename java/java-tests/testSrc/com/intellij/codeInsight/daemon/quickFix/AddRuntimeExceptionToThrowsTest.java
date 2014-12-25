package com.intellij.codeInsight.daemon.quickFix;
import com.intellij.codeInsight.daemon.LightIntentionActionTestCase;
import com.intellij.pom.java.LanguageLevel;

/**
 * @author ven
 */
public class AddRuntimeExceptionToThrowsTest extends LightIntentionActionTestCase {
  public void test() throws Exception { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/addRuntimeExceptionToThrows";
  }
}

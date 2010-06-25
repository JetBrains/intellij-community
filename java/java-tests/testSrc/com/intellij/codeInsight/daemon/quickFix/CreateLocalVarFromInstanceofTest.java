package com.intellij.codeInsight.daemon.quickFix;import com.intellij.codeInsight.daemon.LightIntentionActionTestCase;

/**
 * @author ven
 */
public class CreateLocalVarFromInstanceofTest extends LightIntentionActionTestCase {
  public void test() throws Exception { doAllTests(); }

  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/createLocalVarFromInstanceof";
  }
}

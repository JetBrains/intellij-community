package com.intellij.codeInsight.daemon.quickFix;

/**
 * @author cdr
 */
public class ChangeMethodSignatureFromUsageTest extends LightQuickFix15TestCase {

  public void test() throws Exception { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/changeMethodSignatureFromUsage";
  }
}

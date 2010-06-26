package com.intellij.codeInsight.daemon.quickFix;

/**
 * @author ven
 */
public class CreateMethodFromUsageTest extends LightQuickFix15TestCase {
  public void test() throws Exception { doAllTests(); }

  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/createMethodFromUsage";
  }

}

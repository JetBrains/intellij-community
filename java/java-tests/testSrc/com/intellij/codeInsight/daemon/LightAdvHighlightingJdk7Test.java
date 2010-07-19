package com.intellij.codeInsight.daemon;

import org.jetbrains.annotations.NonNls;

/**
 * This class is for "lightweight" tests only, i.e. those which can run inside default light project set up
 * For "heavyweight" tests use AdvHighlightingTest
 */
public class LightAdvHighlightingJdk7Test extends LightDaemonAnalyzerTestCase {
  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/advHighlighting7";

  private void doTest(boolean checkWarnings, boolean checkInfos) throws Exception {
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", checkWarnings, checkInfos);
  }

  public void testSwitchByString() throws Exception {
    doTest(true, false);
  }
}

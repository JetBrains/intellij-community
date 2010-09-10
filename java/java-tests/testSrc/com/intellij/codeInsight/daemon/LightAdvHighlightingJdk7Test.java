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

  public void testDuplicateAnnotations() throws Exception {
    doTest(false, false);
  }

  public void testSwitchByString() throws Exception {
    doTest(true, false);
  }

  public void testDiamondPos1() throws Exception {
    doTest(false, false);
  }

  public void testDiamondPos2() throws Exception {
    doTest(false, false);
  }

  public void testDiamondPos3() throws Exception {
    doTest(false, false);
  }

  public void testDiamondPos4() throws Exception {
    doTest(false, false);
  }

  public void testDiamondNeg1() throws Exception {
    doTest(false, false);
  }

  public void testDiamondNeg2() throws Exception {
    doTest(false, false);
  }

  public void testDiamondNeg3() throws Exception {
    doTest(false, false);
  }

  public void testDiamondNeg4() throws Exception {
    doTest(false, false);
  }

  public void testDiamondNeg5() throws Exception {
    doTest(false, false);
  }
}

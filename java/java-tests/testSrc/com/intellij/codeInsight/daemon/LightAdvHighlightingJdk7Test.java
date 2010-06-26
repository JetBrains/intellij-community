package com.intellij.codeInsight.daemon;

import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NonNls;

/**
 * This class is for "lightweight" tests only, i.e. those which can run inside default light project set up
 * For "heavyweight" tests use AdvHighlightingTest
 */
public class LightAdvHighlightingJdk7Test extends LightDaemonAnalyzerTestCase {
  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/advHighlighting7";
  private LanguageLevel myOldLanguageLevel;

  private void doTest(boolean checkWarnings, boolean checkInfos) throws Exception {
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", checkWarnings, checkInfos);
  }

  protected void setUp() throws Exception {
    super.setUp();
    myOldLanguageLevel = LanguageLevelProjectExtension.getInstance(getJavaFacade().getProject()).getLanguageLevel();
    LanguageLevelProjectExtension.getInstance(getJavaFacade().getProject()).setLanguageLevel(LanguageLevel.JDK_1_7);
  }

  protected void tearDown() throws Exception {
    LanguageLevelProjectExtension.getInstance(getJavaFacade().getProject()).setLanguageLevel(myOldLanguageLevel);
    super.tearDown();
  }

  public void testSwitchByString() throws Exception {
    doTest(true, false);
  }
}

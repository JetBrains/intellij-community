package com.intellij.codeInsight.daemon;

import org.jetbrains.annotations.NonNls;

/**
 * @author ven
 */
public class AnnotationsHighlightingTest extends LightDaemonAnalyzerTestCase {
  @NonNls
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/annotations";

  private void doTest(boolean checkWarnings) throws Exception {
    doTest(BASE_PATH + "/" + getTestName(true) + ".java", checkWarnings, false);
  }

  public void testNotValueNameOmitted() throws Exception { doTest(false); }
  public void testCannotFindMethod() throws Exception { doTest(false); }
  public void testIncompatibleType1() throws Exception { doTest(false); }
  public void testIncompatibleType2() throws Exception { doTest(false); }
  public void testIncompatibleType3() throws Exception { doTest(false); }
  public void testIncompatibleType4() throws Exception { doTest(false); }
  public void testMissingAttribute() throws Exception { doTest(false); }
  public void testDuplicateAnnotation() throws Exception { doTest(false); }
  public void testNonConstantInitializer() throws Exception { doTest(false); }
  public void testInvalidType() throws Exception { doTest(false); }
  public void testInapplicable() throws Exception { doTest(false); }
  public void testDuplicateAttribute() throws Exception { doTest(false); }
  public void testDuplicateTarget() throws Exception { doTest(false); }
  public void testTypeAnnotations() throws Exception { doTest(false); }

  public void testInvalidPackageAnnotationTarget() throws Exception {
    doTest(BASE_PATH + "/" + getTestName(true) + "/package-info.java", false, false);
  }

  public void testPackageAnnotationNotInPackageInfo() throws Exception {
    doTest(BASE_PATH + "/" + getTestName(true) + "/notPackageInfo.java", false, false);
  }
}

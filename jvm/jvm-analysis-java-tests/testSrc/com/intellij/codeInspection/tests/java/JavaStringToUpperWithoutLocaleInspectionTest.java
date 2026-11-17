package com.intellij.codeInspection.tests.java;

import com.intellij.codeInspection.StringToUpperWithoutLocale2Inspection;
import com.intellij.jvm.analysis.JavaJvmAnalysisTestUtil;
import com.intellij.jvm.analysis.testFramework.JvmInspectionFixture;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.jvm.analysis.internal.testFramework.JvmAnalysisInspectionFixtures.stringToUpperWithoutLocaleInspectionFixture;
import static com.intellij.jvm.analysis.internal.testFramework.JvmAnalysisInspectionFixtures.stringToUpperWithoutLocaleProjectFixture;

@TestDataPath("$CONTENT_ROOT/testData/codeInspection/toUpperWithoutLocale")
@TestApplication
public class JavaStringToUpperWithoutLocaleInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = stringToUpperWithoutLocaleProjectFixture();

  private final TestFixture<JvmInspectionFixture<StringToUpperWithoutLocale2Inspection>> toUpperFixture =
    stringToUpperWithoutLocaleInspectionFixture(projectFixture, JavaJvmAnalysisTestUtil.getTestDataPath() + "/codeInspection/toUpperWithoutLocale");

  @Test
  public void testSimpleCases() {
    toUpperFixture.get().testHighlighting("SimpleCases.java");
    //TODO test after quickfix once it's implemented
  }

  @Test
  public void testNonNlsCases() {
    //TODO test #4 (about equals())
    //TODO test #5 (about @NonNls in the left side of assignment expression)
    //TODO test #6 with assigning method return value to variable? If this is not super hard to support.
    toUpperFixture.get().testHighlighting("NonNlsCases.java");
  }

  //TODO test pkg/subPkg
}

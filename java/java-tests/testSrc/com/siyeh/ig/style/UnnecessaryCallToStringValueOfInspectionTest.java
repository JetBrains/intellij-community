package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixture.INSPECTION_GADGETS_TEST_DATA_PATH;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixtureByTestName;

@TestApplication
public class UnnecessaryCallToStringValueOfInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_8);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixtureByTestName(projectFixture, testName -> getInspection(testName), getEnvironmentClasses(),
      INSPECTION_GADGETS_TEST_DATA_PATH + "com/siyeh/igtest/style/unnecessary_valueof");

  @Test
  public void testUnnecessaryCallToStringValueOf() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testUnnecessaryCallToStringValueOf_all() {
    doAllTest();
  }

  private void doAllTest() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    String name = inspectionFixture.get().getTestName(false);
    myFixture.configureByFile(name + ".java");
    myFixture.testHighlighting(true, false, true);
  }


  @Nullable
  private static InspectionProfileEntry getInspection(String testName) {
    UnnecessaryCallToStringValueOfInspection inspection = new UnnecessaryCallToStringValueOfInspection();
    if (testName.contains("_all")) {
      inspection.reportWithEmptyString = true;
    }
    return inspection;
  }

  private static String[] getEnvironmentClasses() {
    return new String[]{
      "package org.slf4j; public interface Logger { void info(String format, Object... arguments); }",
      "package org.slf4j; public class LoggerFactory { public static Logger getLogger(Class clazz) { return null; }}"};
  }
}
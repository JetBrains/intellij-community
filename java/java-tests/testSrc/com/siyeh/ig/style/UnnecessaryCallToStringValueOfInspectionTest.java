package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UnnecessaryCallToStringValueOfInspectionTest extends LightJavaInspectionTestCase {

  public void testUnnecessaryCallToStringValueOf() {
    doTest();
  }

  public void testUnnecessaryCallToStringValueOf_all() {
    doAllTest();
  }

  private void doAllTest() {
    String name = getTestName(false);
    myFixture.configureByFile(name + ".java");
    myFixture.testHighlighting(true, false, true);
  }


  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    UnnecessaryCallToStringValueOfInspection inspection = new UnnecessaryCallToStringValueOfInspection();
    if (getTestName(true).contains("_all")) {
      inspection.reportWithEmptyString = true;
    }
    return inspection;
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  @Override
  protected String getBasePath() {
    return "/java/java-tests/testData/ig/com/siyeh/igtest/style/unnecessary_valueof";
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[]{
      "package org.slf4j; public interface Logger { void info(String format, Object... arguments); }",
      "package org.slf4j; public class LoggerFactory { public static Logger getLogger(Class clazz) { return null; }}"};
  }
}
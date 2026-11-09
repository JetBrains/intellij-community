// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.lang.annotation.HighlightSeverity;
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
import static org.assertj.core.api.Assertions.assertThat;

@TestApplication
public class BoundedWildcardInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_8);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixtureByTestName(projectFixture, testName -> getInspection(testName), getEnvironmentClasses(),
      INSPECTION_GADGETS_TEST_DATA_PATH + "com/siyeh/igtest/style/bounded_wildcard");

  private static String[] getEnvironmentClasses() {
    return new String[]{
      "package my;" +
      "public interface Processor<T> {" +
      "    boolean process(T t);" +
      "}",
    };
  }

  @Nullable
  private static InspectionProfileEntry getInspection(String testName) {
    BoundedWildcardInspection inspection = new BoundedWildcardInspection();
    if (testName.contains("InvariantSwitchedOff")) {
      inspection.REPORT_INVARIANT_CLASSES = false;
    }
    if (testName.contains("PrivateMethodsSwitchedOff")) {
      inspection.REPORT_PRIVATE_METHODS = false;
    }
    return inspection;
  }

  @Test
  public void testSimple() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    inspectionFixture.get().doTest();
    assertThat(myFixture.doHighlighting(HighlightSeverity.ERROR)).isNullOrEmpty();
  }

  @Test
  public void testInvariantSwitchedOff() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    inspectionFixture.get().doTest();
    assertThat(myFixture.doHighlighting(HighlightSeverity.ERROR)).isNullOrEmpty();
  }

  @Test
  public void testPrivateMethodsSwitchedOff() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    inspectionFixture.get().doTest();
    assertThat(myFixture.doHighlighting(HighlightSeverity.ERROR)).isNullOrEmpty();
  }
}
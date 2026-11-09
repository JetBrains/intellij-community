package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixtureByTestName;

@TestApplication
public class ExpressionComparedToItselfInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21_ANNOTATED);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixtureByTestName(projectFixture, testName -> getInspection(testName));

  @Test
  public void testExpressionComparedToItself() {
    inspectionFixture.get().doTest();
  }
  
  @Test
  public void testExpressionComparedToItselfNoSideEffect() {
    inspectionFixture.get().doTest();
  }

  @Nullable
  private static InspectionProfileEntry getInspection(String testName) {
    ExpressionComparedToItselfInspection inspection = new ExpressionComparedToItselfInspection();
    inspection.ignoreSideEffectConditions = testName.contains("NoSideEffect");
    return inspection;
  }
}
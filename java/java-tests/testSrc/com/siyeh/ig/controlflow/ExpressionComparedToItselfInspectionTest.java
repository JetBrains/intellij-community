package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.fixtures.JavaTestProject;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.junit5.AnalysisHostTest;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.JavaInspectionFixture;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.JavaTestFixtures.javaTestProjectFixture;
import static com.siyeh.ig.InspectionTestFixtures.javaInspectionFixtureByTestName;

@AnalysisHostTest
public class ExpressionComparedToItselfInspectionTest {
  private static final TestFixture<? extends JavaTestProject> projectFixture = javaTestProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21_ANNOTATED);
  private final TestFixture<? extends JavaInspectionFixture> inspectionFixture =
    javaInspectionFixtureByTestName(projectFixture, testName -> getInspection(testName));

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
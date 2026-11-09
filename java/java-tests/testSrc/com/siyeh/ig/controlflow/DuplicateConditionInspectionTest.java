package com.siyeh.ig.controlflow;

import com.intellij.codeInsight.intention.IntentionAction;
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
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixtureByTestName;

@TestApplication
public class DuplicateConditionInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21_ANNOTATED);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixtureByTestName(projectFixture, testName -> getInspection(testName));

  @Test
  public void testDuplicateCondition() {
    inspectionFixture.get().doTest();
  }
  @Test
  public void testDuplicateConditionNoSideEffect() {
    inspectionFixture.get().doTest();
  }
  @Test
  public void testDuplicateBooleanBranch() {
    inspectionFixture.get().doTest();
  }
  @Test
  public void testDuplicateWithNegation() {
    inspectionFixture.get().doTest();
  }
  @Test
  public void testFix() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    inspectionFixture.get().doTest();
    IntentionAction action = myFixture.findSingleIntention("Navigate to duplicate");
    myFixture.checkIntentionPreviewHtml(action, "<p>&rarr; <icon src=\"icon\"/>&nbsp;Fix.java, line #3</p>");
  }

  @Nullable
  private static InspectionProfileEntry getInspection(String testName) {
    DuplicateConditionInspection inspection = new DuplicateConditionInspection();
    inspection.ignoreSideEffectConditions = testName.contains("NoSideEffect");
    return inspection;
  }
}
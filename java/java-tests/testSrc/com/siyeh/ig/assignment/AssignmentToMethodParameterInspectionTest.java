package com.siyeh.ig.assignment;

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ArrayUtilRt;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixture.INSPECTION_GADGETS_TEST_DATA_PATH;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

/**
 * @author bas
 */
@TestApplication
public class AssignmentToMethodParameterInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> null, ArrayUtilRt.EMPTY_STRING_ARRAY,
      INSPECTION_GADGETS_TEST_DATA_PATH + "com/siyeh/igtest/assignment/method_parameter");

  @Test
  public void testAssignmentToMethodParameterMissesCompoundAssign() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    myFixture.enableInspections(new AssignmentToMethodParameterInspection());
    inspectionFixture.get().doTest();
  }

  @Test
  public void testIgnoreTransformationOfParameter() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    final AssignmentToMethodParameterInspection inspection = new AssignmentToMethodParameterInspection();
    inspection.ignoreTransformationOfOriginalParameter = true;
    myFixture.enableInspections(inspection);
    inspectionFixture.get().doTest();
  }
}
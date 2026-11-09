package com.siyeh.ig.naming;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ArrayUtilRt;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class ConstantNamingConventionInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> getInspection(), ArrayUtilRt.EMPTY_STRING_ARRAY, null, FieldNamingConventionInspection.class);

  @Test
  public void testConstantNamingConvention() {
    inspectionFixture.get().doTest();
  }

  @Nullable
  private static InspectionProfileEntry getInspection() {
    FieldNamingConventionInspection inspection = new FieldNamingConventionInspection();
    inspection.setEnabled(true,new ConstantNamingConvention().getShortName());
    return inspection;
  }
}
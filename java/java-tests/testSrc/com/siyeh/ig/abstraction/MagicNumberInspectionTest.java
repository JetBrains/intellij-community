package com.siyeh.ig.abstraction;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class MagicNumberInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> getInspection());

  @Test
  public void testMagicNumber() {
    inspectionFixture.get().doTest();
  }

  private static InspectionProfileEntry getInspection() {
    final MagicNumberInspection tool = new MagicNumberInspection();
    tool.ignoreInHashCode = true;
    tool.ignoreInAnnotations = true;
    tool.ignoreInitialCapacity = true;
    return tool;
  }
}
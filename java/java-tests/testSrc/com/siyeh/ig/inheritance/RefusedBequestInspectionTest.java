// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.inheritance;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class RefusedBequestInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> getInspection(), getEnvironmentClasses());

  @Test
  public void testRefusedBequest() { inspectionFixture.get().doTest(); }
  @Test
  public void testCloneCallsSuperClone() { inspectionFixture.get().doTest(); }
  @Test
  public void testSetupCallsSuperSetup() { inspectionFixture.get().doTest(); }
  @Test
  public void testFinalizeCallsSuperFinalize() { inspectionFixture.get().doTest(); }
  @Test
  public void testGenericsSignatures() { inspectionFixture.get().doTest(); }
  @Test
  public void testDefaultMethods() { inspectionFixture.get().doTest(); }

  @Test
  public void testNonTrivialSuperMethod() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    final RefusedBequestInspection inspection = new RefusedBequestInspection();
    inspection.onlyReportWhenAnnotated = false;
    inspection.ignoreEmptySuperMethods = true;
    myFixture.enableInspections(inspection);
    inspectionFixture.get().doTest();
  }

  @Test
  public void testSetupCallsSuperSetup2() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    myFixture.enableInspections(new RefusedBequestInspection());
    inspectionFixture.get().doTest();
  }

  @Nullable
  private static InspectionProfileEntry getInspection() {
    RefusedBequestInspection inspection = new RefusedBequestInspection();
    inspection.onlyReportWhenAnnotated = false;
    inspection.ignoreDefaultSuperMethods = true;
    return inspection;
  }

  @SuppressWarnings("RedundantThrows")
  private static String[] getEnvironmentClasses() {
    return new String[] {
      """
package org.junit;
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Before {
}""",

      "package junit.framework;" +
      "public abstract class TestCase {" +
      "    protected void setUp() throws Exception {}" +
      "    protected void tearDown() throws Exception {}" +
      "}"
    };
  }
}

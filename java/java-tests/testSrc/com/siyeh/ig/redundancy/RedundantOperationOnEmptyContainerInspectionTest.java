// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.redundancy;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class RedundantOperationOnEmptyContainerInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_8_ANNOTATED);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new RedundantOperationOnEmptyContainerInspection());

  @Test
  public void testIterationOverEmptyContainer() {
    inspectionFixture.get().doTest();
  }
  @Test
  public void testMethodCallsOnEmptyContainer() {
    inspectionFixture.get().doTest();
  }
  @Test
  public void testStaticInitializer() {
    inspectionFixture.get().doTest();
  }
  @Test
  public void testEmptyCollectionReturnThis() { 
    inspectionFixture.get().doTest();
  }
  @Test
  public void testTernaryInQualifier() { 
    inspectionFixture.get().doTest();
  }
  @Test
  public void testAnonymousClass() { 
    inspectionFixture.get().doTest();
  }
  @Test
  public void testSpringCollectionUtils() { 
    inspectionFixture.get().doTest();
  }
}
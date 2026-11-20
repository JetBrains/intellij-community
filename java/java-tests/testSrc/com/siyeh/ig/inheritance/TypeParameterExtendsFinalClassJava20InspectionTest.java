// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.inheritance;

import com.intellij.testFramework.fixtures.JavaTestProject;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.junit5.AnalysisHostTest;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.JavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.JavaTestFixtures.javaTestProjectFixture;
import static com.siyeh.ig.InspectionTestFixtures.javaInspectionFixture;

@AnalysisHostTest
public class TypeParameterExtendsFinalClassJava20InspectionTest {
  private static final TestFixture<? extends JavaTestProject> projectFixture = javaTestProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_X);
  private final TestFixture<? extends JavaInspectionFixture> inspectionFixture =
    javaInspectionFixture(projectFixture, () -> new TypeParameterExtendsFinalClassInspection());

  @Test
  public void testTypeParameterExtendsFinalClassJava20() {
    inspectionFixture.get().doTest();
  }
}

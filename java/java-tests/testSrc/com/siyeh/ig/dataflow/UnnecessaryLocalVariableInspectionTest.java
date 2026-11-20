// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.dataflow;

import com.intellij.testFramework.fixtures.JavaTestProject;
import com.intellij.testFramework.junit5.AnalysisHostTest;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.JavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.JavaTestFixtures.javaTestProjectFixture;
import static com.siyeh.ig.InspectionTestFixtures.javaInspectionFixture;

@AnalysisHostTest
public class UnnecessaryLocalVariableInspectionTest {
  private static final TestFixture<? extends JavaTestProject> projectFixture = javaTestProjectFixture();
  private final TestFixture<? extends JavaInspectionFixture> inspectionFixture =
    javaInspectionFixture(projectFixture, () -> new UnnecessaryLocalVariableInspection());

  @Test
  public void testUnnecessaryLocalVariable() { inspectionFixture.get().doTest(); }
  
  @Test
  public void testCastNecessary() { inspectionFixture.get().doTest(); }

  @Test
  public void testTree() { inspectionFixture.get().doTest(); }

  @Test
  public void testSwitchExpression() { inspectionFixture.get().doTest(); }

  @Test
  public void testEffectivelyFinalVariableInGuard() { inspectionFixture.get().doTest(); }

  @Test
  public void testUnknownExpression() { inspectionFixture.get().doTest(); }
}
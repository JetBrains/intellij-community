// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.java.codeInspection.DataFlowInspectionFixtureKt.dataFlowInspectionFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class DataFlowInspectionPrimitivesInPatternsTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_23);
  private final TestFixture<DataFlowInspectionFixture> dataFlow =
    dataFlowInspectionFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/inspection/dataFlow/fixture/");

  @Test
  public void testInstanceofFromBoxedObjectToPrimitive() {
    dataFlow.get().doTest();
  }

  @Test
  public void testInstanceofFromObjectToPrimitive() {
    dataFlow.get().doTest();
  }

  @Test
  public void testInstanceofFromPrimitiveToObject() {
    dataFlow.get().doTest();
  }

  @Test
  public void testInstanceofFromPrimitiveToPrimitive() {
    dataFlow.get().doTest();
  }

  @Test
  public void testSwitchWithPrimitive() {
    dataFlow.get().doTest();
  }

  @Test
  public void testSwitchFloatRepresentation() {
    dataFlow.get().doTest();
  }

  @Test
  public void testSwitchBoxedFloatRepresentation() {
    dataFlow.get().doTest();
  }
}
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.miscGenerics.RedundantArrayForVarargsCallInspection;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class RedundantArrayForVarargsCallInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new RedundantArrayForVarargsCallInspection());

  @BeforeEach
  void setUpTestDataPath() {
    inspectionFixture.get().getFixture().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/inspection/redundantArrayForVarargs/");
  }

  @Test
  public void testIDEADEV15215() { inspectionFixture.get().doTest(); }
  @Test
  public void testIDEADEV25923() { inspectionFixture.get().doTest(); }
  @Test
  public void testNestedArray() { inspectionFixture.get().doTest(); }
  @Test
  public void testCheckEnumConstant() { inspectionFixture.get().doTest(); }
  @Test
  public void testGeneric() { inspectionFixture.get().doTest(); }
  @Test
  public void testRawArray() { inspectionFixture.get().doTest(); }
  @Test
  public void testPolymorphicSignature() { inspectionFixture.get().doTest(); }
  @Test
  public void testValidSubtype() { inspectionFixture.get().doTest(); }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.OptionalIsPresentInspection;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class OptionalIsPresentInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_11_ANNOTATED);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new OptionalIsPresentInspection());

  @BeforeEach
  void setUpTestDataPath() {
    inspectionFixture.get().getFixture().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/inspection/optionalIsPresent/");
  }

  @Test
  public void testAssignment() { inspectionFixture.get().doTest(); }

  @Test
  public void testConsumer() { inspectionFixture.get().doTest(); }

  @Test
  public void testConsumerIndirect() { inspectionFixture.get().doTest(); }

  @Test
  public void testConsumerIndirectAmbiguousOverload() { inspectionFixture.get().doTest(); }

  @Test
  public void testConsumerIndirectComment() { inspectionFixture.get().doTest(); }

  @Test
  public void testConsumerIndirectFieldInaccessible() { inspectionFixture.get().doTest(); }

  @Test
  public void testConsumerIndirectTooComplex_1() { inspectionFixture.get().doTest(); }

  @Test
  public void testConsumerIndirectTooComplex_2() { inspectionFixture.get().doTest(); }

  @Test
  public void testConsumerIndirectTypeMismatch() { inspectionFixture.get().doTest(); }

  @Test
  public void testOptional() { inspectionFixture.get().doTest(); }
}

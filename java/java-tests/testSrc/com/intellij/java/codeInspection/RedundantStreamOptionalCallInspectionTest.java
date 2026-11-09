// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.RedundantStreamOptionalCallInspection;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class RedundantStreamOptionalCallInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_17);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new RedundantStreamOptionalCallInspection());

  @BeforeEach
  void setUpTestDataPath() {
    inspectionFixture.get().getFixture().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + TEST_DATA_DIR);
  }

  public static final String TEST_DATA_DIR = "/inspection/redundantStreamOptionalCall/";

  @Test
  public void testRedundantStreamOptionalCall() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testExplicitNullabilityAnnotation() {
    inspectionFixture.get().doTest();
  }

  public static class RedundantStreamOptionalCallFixTest extends LightQuickFixParameterizedTestCase {
    @Override
    protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
      return new LocalInspectionTool[]{new RedundantStreamOptionalCallInspection()};
    }

    @Override
    protected String getBasePath() {
      return TEST_DATA_DIR;
    }
  }
}

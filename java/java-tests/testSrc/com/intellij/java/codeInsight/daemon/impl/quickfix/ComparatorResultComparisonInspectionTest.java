// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.ComparatorResultComparisonInspection;
import com.intellij.codeInspection.LocalInspectionTool;
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
public class ComparatorResultComparisonInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new ComparatorResultComparisonInspection());

  @BeforeEach
  void setUpTestDataPath() {
    inspectionFixture.get().getFixture().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + TEST_DATA_DIR);
  }

  static final String TEST_DATA_DIR = "/codeInsight/daemonCodeAnalyzer/quickFix/comparatorResultComparison/";

  @Test
  public void testComparatorResultComparison() {
    inspectionFixture.get().doTest();
  }

  public static class ComparatorResultComparisonInspectionFixTest extends LightQuickFixParameterizedTestCase {
    @Override
    protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
      return new LocalInspectionTool[]{new ComparatorResultComparisonInspection()};
    }

    @Override
    protected String getBasePath() {
      return TEST_DATA_DIR;
    }
  }
}

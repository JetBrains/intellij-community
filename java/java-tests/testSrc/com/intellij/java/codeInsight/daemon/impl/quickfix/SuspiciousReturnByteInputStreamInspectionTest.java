// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixture;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.SuspiciousReturnByteInputStreamInspection;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixFixture;
import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixTestFiles;
import static com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_8;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class SuspiciousReturnByteInputStreamInspectionTest {
  private static final String TEST_DATA_PATH =
    JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/quickFix/suspiciousReturnByteInputStream";
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(JAVA_8);
  private final TestFixture<LightQuickFixFixture> quickFixFixture =
    lightQuickFixFixture(projectFixture, TEST_DATA_PATH, () -> List.of(createInspections()));

  static List<String> files() {
    return lightQuickFixTestFiles(TEST_DATA_PATH);
  }

  @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
  @MethodSource("files")
  void parameterized(String fileName) {
    quickFixFixture.get().doTest(fileName);
  }

  private static LocalInspectionTool @NotNull [] createInspections() {
    SuspiciousReturnByteInputStreamInspection inspection = new SuspiciousReturnByteInputStreamInspection();
    return new LocalInspectionTool[]{inspection};
  }
}
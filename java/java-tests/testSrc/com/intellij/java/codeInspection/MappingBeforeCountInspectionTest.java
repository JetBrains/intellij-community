// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixture;
import com.intellij.codeInspection.MappingBeforeCountInspection;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixFixture;
import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixTestFiles;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class MappingBeforeCountInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_17);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new MappingBeforeCountInspection());

  @BeforeEach
  void setUpTestDataPath() {
    inspectionFixture.get().getFixture().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + TEST_DATA_DIR);
  }

  public static final String TEST_DATA_DIR = "/inspection/mappingBeforeCount/";

  @Test
  public void testMappingBeforeCount() {
    inspectionFixture.get().doTest();
  }

  @TestApplication
  public static class MappingBeforeCountInspectionFixTest {
    private static final String FIX_TEST_DATA_PATH = JavaTestUtil.getJavaTestDataPath() + TEST_DATA_DIR;
    private static final TestFixture<LightJavaProject> fixProjectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
    private final TestFixture<LightQuickFixFixture> fixQuickFixFixture =
      lightQuickFixFixture(fixProjectFixture, FIX_TEST_DATA_PATH, () -> List.of(new MappingBeforeCountInspection()));

    static List<String> files() {
      return lightQuickFixTestFiles(FIX_TEST_DATA_PATH);
    }

    @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
    @MethodSource("files")
    void parameterized(String fileName) {
      fixQuickFixFixture.get().doTest(fileName);
    }
  }
}

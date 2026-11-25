// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.internationalization;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixFixture;
import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixLanguageLevel;
import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixTestFiles;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

/**
 * The files with a language level in the name, for example `Java10`, use the JDK 11 project. The other files use the JDK 9 project.
 */
@TestApplication
public class ImplicitDefaultCharsetUsageInspectionFixTest {
  private static final String TEST_DATA_PATH =
    JavaTestUtil.getJavaTestDataPath() + "/ig/com/siyeh/igtest/internationalization/implicit_default_charset_usage";
  private static final TestFixture<LightJavaProject> java9ProjectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_9);
  private static final TestFixture<LightJavaProject> java11ProjectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_11);
  private final TestFixture<LightQuickFixFixture> java9QuickFixFixture =
    lightQuickFixFixture(java9ProjectFixture, TEST_DATA_PATH, () -> List.of(new ImplicitDefaultCharsetUsageInspection()));
  private final TestFixture<LightQuickFixFixture> java11QuickFixFixture =
    lightQuickFixFixture(java11ProjectFixture, TEST_DATA_PATH, () -> List.of(new ImplicitDefaultCharsetUsageInspection()));

  static List<String> files() {
    return lightQuickFixTestFiles(TEST_DATA_PATH);
  }

  @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
  @MethodSource("files")
  void parameterized(String fileName) {
    LightQuickFixFixture quickFix = lightQuickFixLanguageLevel(fileName) != null ? java11QuickFixFixture.get() : java9QuickFixFixture.get();
    quickFix.doTest(fileName);
  }
}

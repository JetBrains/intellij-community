// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.JavaTestUtil;
import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixture;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixOptions;
import com.intellij.codeInspection.ExplicitTypeCanBeDiamondInspection;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixFixture;
import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixTestFiles;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

@TestApplication
public class Simplify2DiamondInspectionsTest {
  private static final String TEST_DATA_PATH =
    JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/quickFix/explicit2diamond";
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.JDK_1_8));
  private final TestFixture<LightQuickFixFixture> quickFixFixture =
    lightQuickFixFixture(projectFixture, TEST_DATA_PATH, List::of, LightQuickFixOptions.DEFAULT.withLanguageLevelFromFileName());

  static List<String> files() {
    return lightQuickFixTestFiles(TEST_DATA_PATH);
  }

  @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
  @MethodSource("files")
  void parameterized(String fileName) {
    quickFixFixture.get().doTest(fileName);
  }

  private Boolean myAlignment;

  @BeforeEach
  void setUpSettingsAndInspections() {
    quickFixFixture.get().getFixture().enableInspections(new ExplicitTypeCanBeDiamondInspection());
    CommonCodeStyleSettings settings = getSettings();
    myAlignment = settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS;
    settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
  }

  @AfterEach
  void restoreSettings() {
    if (myAlignment != null) {
      getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = myAlignment;
    }
  }

  private CommonCodeStyleSettings getSettings() {
    return CodeStyle.getSettings(quickFixFixture.get().getFixture().getProject()).getCommonSettings(JavaLanguage.INSTANCE);
  }
}

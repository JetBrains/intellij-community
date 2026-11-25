// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.FileBasedArgumentProvider;
import com.intellij.testFramework.FileBasedTestCaseHelperEx;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;


/**
 * A JUnit 5 quick fix test with one test for each `before` file of {@link #getTestDataPath()}.
 * Each test runs {@link LightQuickFixParameterizedTestCase5Kt#doLightQuickFixFileTest} with the `before` file and the `after` file.
 * <p>
 * A subclass gives the code insight fixture of the test in {@link #getFixture()}, for example from
 * {@link com.intellij.testFramework.fixtures.LightJavaFixturesKt#lightJavaCodeInsightFixture}.
 * This class sets the test data path of the fixture before each test.
 * A subclass declares {@link #getTestDataPath()} itself, because {@link FileBasedArgumentProvider} finds the method on the test class.
 *
 * @deprecated Use {@link LightQuickFixFixtureKt#lightQuickFixFixture} with a parameterized test method and
 * {@link LightQuickFixFixtureKt#lightQuickFixTestFiles}.
 */
@Deprecated
public abstract class LightQuickFixFileBasedTest implements FileBasedTestCaseHelperEx {
  public abstract @NotNull JavaCodeInsightTestFixture getFixture();

  public abstract @NotNull String getTestDataPath();

  @BeforeEach
  public void setUpQuickFixTestDataPath() {
    getFixture().setTestDataPath(getTestDataPath());
  }

  @Override
  public @NotNull String getRelativeBasePath() {
    return "";
  }

  @Override
  public @Nullable String getFileSuffix(@NotNull String fileName) {
    return fileName.startsWith(LightQuickFixTestCase.BEFORE_PREFIX) ? fileName.substring(LightQuickFixTestCase.BEFORE_PREFIX.length()) : null;
  }

  @Override
  public @Nullable String getBaseName(@NotNull String fileAfterSuffix) {
    return fileAfterSuffix.startsWith(LightQuickFixTestCase.AFTER_PREFIX) ? fileAfterSuffix.substring(LightQuickFixTestCase.AFTER_PREFIX.length()) : null;
  }

  /**
   * @return false if the action hint text is a prefix of the action text, like in {@link ActionHint#parse(com.intellij.psi.PsiFile, String, boolean)}
   */
  protected boolean isExactActionHintMatch() {
    return true;
  }

  /**
   * Returns the language level of the module for the test of the `before` file with the name suffix {@code fileName}, or null.
   * With null, the module keeps its level. Otherwise the test sets the level of the module until the end of the test.
   *
   * @see #getLanguageLevelOfFileName(String)
   */
  protected @Nullable LanguageLevel getLanguageLevel(@NotNull String fileName) {
    return null;
  }

  /**
   * Returns the language level that the name suffix {@code fileName} of a `before` file selects, or null.
   * A name that ends with `JavaXYZ` or `JavaXYZ.java` selects the Java version XYZ, for example `Java1.4.java` or `Java9.java`.
   * A name that ends with `JavaXYZPreview.java` selects the preview level of XYZ.
   * It is the rule of {@link com.intellij.testFramework.LightJavaCodeInsightTestCase#getLanguageLevel()}.
   */
  public static @Nullable LanguageLevel getLanguageLevelOfFileName(@NotNull String fileName) {
    return LightQuickFixFixtureKt.lightQuickFixLanguageLevel(fileName);
  }

  @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
  @ArgumentsSource(FileBasedArgumentProvider.class)
  public void parameterized(String fileName) {
    LanguageLevel level = getLanguageLevel(fileName);
    if (level != null) {
      JavaCodeInsightTestFixture fixture = getFixture();
      IdeaTestUtil.setModuleLanguageLevel(fixture.getModule(), level, fixture.getTestRootDisposable());
    }
    LightQuickFixParameterizedTestCase5Kt.doLightQuickFixFileTest(getFixture(), getTestDataPath(), fileName, isExactActionHintMatch());
  }
}

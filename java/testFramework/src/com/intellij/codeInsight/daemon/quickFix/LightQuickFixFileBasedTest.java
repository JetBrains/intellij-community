// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.testFramework.FileBasedArgumentProvider;
import com.intellij.testFramework.FileBasedTestCaseHelperEx;
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
 */
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

  @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
  @ArgumentsSource(FileBasedArgumentProvider.class)
  public void parameterized(String fileName) {
    LightQuickFixParameterizedTestCase5Kt.doLightQuickFixFileTest(getFixture(), getTestDataPath(), fileName);
  }
}

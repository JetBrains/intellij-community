// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixture;
import com.intellij.codeInspection.dataFlow.OptionalOfNullableMisuseInspection;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixFixture;
import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixTestFiles;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class ReplaceFromOfNullableFixTest {
  private static final String TEST_DATA_PATH =
    JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/quickFix/replaceFromOfNullable";
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_LATEST_WITH_LATEST_JDK);
  private final TestFixture<LightQuickFixFixture> quickFixFixture = lightQuickFixFixture(projectFixture, TEST_DATA_PATH);

  static List<String> files() {
    return lightQuickFixTestFiles(TEST_DATA_PATH);
  }

  @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
  @MethodSource("files")
  void parameterized(String fileName) {
    quickFixFixture.get().doTest(fileName);
  }

  @BeforeEach
  public void setupInspections(TestInfo testInfo) {
    quickFixFixture.get().getFixture().enableInspections(OptionalOfNullableMisuseInspection.class);
    if (testInfo.getDisplayName().contains("Guava")) {
      addGuavaOptional(quickFixFixture.get().getFixture());
    }
  }

  public static void addGuavaOptional(JavaCodeInsightTestFixture fixture) {
    fixture.addClass("""
        package com.google.common.base;
        public abstract class Optional<T> {
          public static <T> Optional<T> absent() { }

          public static <T> Optional<T> of(@org.jetbrains.annotations.NotNull T reference) { }

          public static <T> Optional<T> fromNullable(T nullableReference) { }
        }
        """);
  }
}

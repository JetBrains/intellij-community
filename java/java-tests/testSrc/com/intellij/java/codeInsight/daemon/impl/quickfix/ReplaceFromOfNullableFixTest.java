// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixFileBasedTest;
import com.intellij.codeInspection.dataFlow.OptionalOfNullableMisuseInspection;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class ReplaceFromOfNullableFixTest extends LightQuickFixFileBasedTest {
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_LATEST_WITH_LATEST_JDK);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Override
  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return codeInsightFixture.get();
  }

  @BeforeEach
  public void setupInspections(TestInfo testInfo) {
    getFixture().enableInspections(OptionalOfNullableMisuseInspection.class);
    if (testInfo.getDisplayName().contains("Guava")) {
      addGuavaOptional(getFixture());
    }
  }

  @Override
  public @NotNull String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/quickFix/replaceFromOfNullable";
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

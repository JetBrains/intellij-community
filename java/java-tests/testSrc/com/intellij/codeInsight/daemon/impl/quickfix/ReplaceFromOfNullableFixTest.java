// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase5;
import com.intellij.codeInspection.dataFlow.OptionalOfNullableMisuseInspection;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;

public class ReplaceFromOfNullableFixTest extends LightQuickFixParameterizedTestCase5 {
  @BeforeEach
  public void setupInspections() {
    getFixture().enableInspections(OptionalOfNullableMisuseInspection.class);
    if (getTestNameRule().getDisplayName().contains("Guava")) {
      addGuavaOptional(getFixture());
    }
  }

  @Override
  protected @NotNull String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/replaceFromOfNullable";
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

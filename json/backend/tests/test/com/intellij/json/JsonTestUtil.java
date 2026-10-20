// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json;

import com.intellij.application.options.CodeStyle;
import com.intellij.json.formatter.JsonCodeStyleSettings;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.Objects;

/**
 * The JSON code style helpers for a JUnit 5 test.
 * It replaces the JUnit 3 base {@code JsonTestCase}.
 * <p>
 * The test data of the JSON tests is in {@code $PROJECT_ROOT/community/json/backend/tests/testData}.
 * Put this path, or a directory under it, in the {@code @TestDataPath} of the test class.
 * <p>
 * The code insight fixture gives each test temporary code style settings, so a test can change them.
 */
@TestOnly
public final class JsonTestUtil {
  private JsonTestUtil() {
  }

  public static @NotNull CodeStyleSettings getCodeStyleSettings(@NotNull Project project) {
    return CodeStyle.getSettings(project);
  }

  public static @NotNull CommonCodeStyleSettings getCommonCodeStyleSettings(@NotNull Project project) {
    return getCodeStyleSettings(project).getCommonSettings(JsonLanguage.INSTANCE);
  }

  public static @NotNull JsonCodeStyleSettings getCustomCodeStyleSettings(@NotNull Project project) {
    return getCodeStyleSettings(project).getCustomSettings(JsonCodeStyleSettings.class);
  }

  public static @NotNull CommonCodeStyleSettings.IndentOptions getIndentOptions(@NotNull Project project) {
    return Objects.requireNonNull(getCommonCodeStyleSettings(project).getIndentOptions());
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

public final class JSpecifyTestUtil {
  private JSpecifyTestUtil() {
  }

  public static void addJSpecifyNullMarked(@NotNull JavaCodeInsightTestFixture fixture) {
    @Language("JAVA") String nullMarked =
      """
        package org.jspecify.annotations;
        import java.lang.annotation.*;
        @Target({ElementType.TYPE, ElementType.METHOD, ElementType.MODULE})
        public @interface NullMarked {}""";
    fixture.addClass(nullMarked);
    @Language("JAVA") String nullUnmarked =
      """
        package org.jspecify.annotations;
        import java.lang.annotation.*;
        @Target({ElementType.TYPE, ElementType.METHOD, ElementType.MODULE})
        public @interface NullUnmarked {}""";
    fixture.addClass(nullUnmarked);
  }

  public static void addJSpecifyNonNull(@NotNull JavaCodeInsightTestFixture fixture) {
    @Language("JAVA") String nonNull =
      """
        package org.jspecify.annotations;
        import java.lang.annotation.*;
        @Target(ElementType.TYPE_USE)
        public @interface NonNull {
        }""";
    fixture.addClass(nonNull);
  }
}
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.macro;

import com.intellij.openapi.util.Predicates;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Holds common predicates which select a subset of {@link Macro macros}.
 */
public final class CommonMacroFilters {
  private CommonMacroFilters() { }

  private static final Pattern CAMEL_HUMP_START_PATTERN = Pattern.compile("(?<=[\\p{Lower}\\p{Digit}])(?![\\p{Lower}\\p{Digit}])");

  public static final @NotNull Predicate<? super Macro> ALL = Predicates.alwaysTrue();
  public static final @NotNull Predicate<? super Macro> NONE = Predicates.alwaysFalse();

  public static final @NotNull Predicate<? super Macro> ANY_PATH =
    m -> nameContains(m, "File") ||
         nameContains(m, "Dir") ||
         m instanceof ContentRootMacro ||
         m instanceof FilePromptMacro;

  public static final @NotNull Predicate<? super Macro> DIRECTORY_PATH =
    m -> nameContains(m, "Dir") ||
         m instanceof ContentRootMacro ||
         m instanceof FilePromptMacro;

  public static final @NotNull Predicate<? super Macro> FILE_PATH =
    m -> nameContains(m, "File") && !nameContains(m, "Dir") ||
         m instanceof FilePromptMacro;

  private static boolean nameContains(@NotNull Macro m, @NotNull String part) {
    final String[] nameParts = CAMEL_HUMP_START_PATTERN.split(m.getName());
    return ArrayUtil.contains(part, nameParts);
  }
}

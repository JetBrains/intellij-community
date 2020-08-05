// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.analysis;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

/**
 * @deprecated use other bundles ({@link AnalysisBundle}, {@link com.intellij.codeInsight.CodeInsightBundle}) instead
 */
@Deprecated
public final class AnalysisScopeBundle extends DynamicBundle {
  @NonNls private static final String BUNDLE = "messages.AnalysisScopeBundle";
  private static final AnalysisScopeBundle INSTANCE = new AnalysisScopeBundle();

  private AnalysisScopeBundle() { super(BUNDLE); }

  @NotNull
  public static @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }

  @NotNull
  public static Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getLazyMessage(key, params);
  }
}
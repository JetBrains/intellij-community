// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.analysis;

import com.intellij.core.CoreDeprecatedMessagesBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated use other bundles ({@link AnalysisBundle}, {@link com.intellij.codeInsight.CodeInsightBundle}) instead
 */
@Deprecated(forRemoval = true)
public final class AnalysisScopeBundle {
  private AnalysisScopeBundle() {
  }

  @NotNull
  public static @Nls String message(@NotNull String key, Object @NotNull ... params) {
    return CoreDeprecatedMessagesBundle.message(key, params);
  }
}
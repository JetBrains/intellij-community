// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.analysis;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class JvmAnalysisBundle extends DynamicBundle {
  public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return ourInstance.getMessage(key, params);
  }

  @NonNls private static final String BUNDLE = "messages.JvmAnalysisBundle";
  private static final JvmAnalysisBundle ourInstance = new JvmAnalysisBundle();

  private JvmAnalysisBundle() {
    super(BUNDLE);
  }
}

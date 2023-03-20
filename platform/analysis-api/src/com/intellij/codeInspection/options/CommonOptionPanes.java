// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.util.ArrayUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.*;

public final class CommonOptionPanes {
  private CommonOptionPanes() {}

  public static @NotNull OptPane conventions(@Language("jvm-field-name") @NonNls @NotNull String minLengthProperty,
                                             @Language("jvm-field-name") @NonNls @NotNull String maxLengthProperty,
                                             @Language("jvm-field-name") @NonNls @NotNull String regexProperty,
                                             @NotNull OptRegularComponent @NotNull ... extraComponents) {
    OptRegularComponent[] components = {
      string(regexProperty, InspectionsBundle.message("label.pattern"), 30, new RegexValidator()),
      number(minLengthProperty, InspectionsBundle.message("label.min.length"), 1, 1_000_000),
      number(maxLengthProperty, InspectionsBundle.message("label.max.length"), 1, 1_000_000)
    };
    return pane(ArrayUtil.mergeArrays(components, extraComponents));
  }
}

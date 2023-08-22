// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.options;

import com.intellij.codeInspection.options.OptCustom;
import com.intellij.codeInspection.options.OptDropdown;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.util.AccessModifier;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.dropdown;

/**
 * Common controls used in Java inspection options
 */
public final class JavaInspectionControls {
  public static @NotNull OptCustom button(@NotNull JavaInspectionButtons.ButtonKind kind) {
    return new JavaInspectionButtons().component(kind);
  }

  public static @NotNull OptDropdown visibilityChooser(@Language("jvm-field-name") @NotNull String stringProperty,
                                                       @NlsContexts.Label String splitLabel) {
    return dropdown(stringProperty, splitLabel, AccessModifier.class, AccessModifier::toString);
  }
}

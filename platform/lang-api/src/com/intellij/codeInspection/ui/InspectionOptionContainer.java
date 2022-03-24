// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ui;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.HtmlChunk;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An interface for inspection options that provides partial introspection data
 * for possible options.
 */
public interface InspectionOptionContainer {
  /**
   * @param property property name
   * @return label for the checkbox that changes the specified property
   * @throws IllegalArgumentException if there's no such checkbox
   */
  @NotNull HtmlChunk getLabelForCheckbox(@NotNull @NonNls String property);
}

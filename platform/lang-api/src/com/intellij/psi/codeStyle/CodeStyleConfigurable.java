// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.NotNull;

/**
 * A configurable used for any code style settings. Allows to operate with model settings updated upon UI changes.
 */
public interface CodeStyleConfigurable extends Configurable {
  /**
   * Reset the UI to the given code style settings.
   *
   * @param settings The settings to update the UI from.
   */
  void reset(@NotNull CodeStyleSettings settings);

  /**
   * Apply the current UI changes to the given settings.
   *
   * @param settings The settings to apply the UI changes to.
   * @throws ConfigurationException If the UI contains incorrect values.
   */
  void apply(@NotNull CodeStyleSettings settings) throws ConfigurationException;
}

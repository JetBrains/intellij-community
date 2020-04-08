// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options;

import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @param <O> type of options, which are handled by this editor
 */
@Experimental
public interface OptionEditor<@NotNull O> {

  /**
   * @return UI for editing the options
   */
  @NotNull JComponent getComponent();

  /**
   * @return new options object, which represents the UI state
   */
  @Contract("-> new")
  O result();
}

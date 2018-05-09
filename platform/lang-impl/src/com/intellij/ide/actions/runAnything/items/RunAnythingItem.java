// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.items;

import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * {@link RunAnythingItem} represents an item of 'Run Anything' list
 */
public abstract class RunAnythingItem {
  /**
   * Returns text presentation of command
   */
  @NotNull
  public abstract String getCommand();

  /**
   * Creates current item {@link Component}
   *
   * @param isSelected true if item is selected in the list
   */
  @NotNull
  public abstract Component createComponent(boolean isSelected);
}

// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.items;

import com.intellij.openapi.actionSystem.DataContext;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * {@link RunAnythingItem} represents an item of 'Run Anything' list
 *
 * @param <T> user object of the item
 */
public abstract class RunAnythingItem<T> {
  /**
   * Returns user object of current item
   */
  @NotNull
  public abstract T getValue();

  /**
   * Returns text presentation of {@link T}
   */
  @NotNull
  public abstract String getText();

  /**
   * Returns advertisement text painted under the input field
   */
  @NotNull
  public abstract String getAdText();

  /**
   * Returns icon of {@link T}
   */
  @NotNull
  public abstract Icon getIcon();

  /**
   * Creates current item {@link Component}
   *
   * @param isSelected true if item is selected in the list
   */
  @NotNull
  public abstract Component createComponent(boolean isSelected);

  /**
   * Sends statistic if current item action is being executed
   * @param dataContext Use {@link DataContext} to extract focus owner component, original action event, working directory, module and project
   */
  protected void triggerUsage(@NotNull DataContext dataContext) {}

  /**
   * Executes specific action on choosing current item in the list
   *
   * @param dataContext Use {@link DataContext} to extract focus owner component, original action event, working directory, module and project
   */
  public void run(@NotNull DataContext dataContext) {
    triggerUsage(dataContext);
  }
}

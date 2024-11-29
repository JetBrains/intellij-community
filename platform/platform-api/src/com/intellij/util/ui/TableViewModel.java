// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.table.AbstractTableModel;
import java.util.List;

public abstract class TableViewModel<Item> extends AbstractTableModel implements SortableColumnModel {
  /**
   * Set the model source data to make UI show the data from this list
   * @param items must be mutable to be able to accommodate the changes from UI
   */
  @Contract(mutates = "param1")
  public abstract void setItems(@NotNull List<Item> items);
  @Unmodifiable
  public abstract @NotNull List<Item> getItems();
}
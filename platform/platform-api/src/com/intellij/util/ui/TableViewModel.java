// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import org.jetbrains.annotations.NotNull;

import javax.swing.table.AbstractTableModel;
import java.util.List;

public abstract class TableViewModel<Item> extends AbstractTableModel implements SortableColumnModel {
  public abstract void setItems(@NotNull List<Item> items);
  public abstract @NotNull List<Item> getItems();
}
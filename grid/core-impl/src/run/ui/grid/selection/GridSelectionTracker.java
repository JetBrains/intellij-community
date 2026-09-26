package com.intellij.database.run.ui.grid.selection;

import org.jetbrains.annotations.NotNull;

public interface GridSelectionTracker {
  void performOperation(@NotNull GridSelectionTracker.Operation operation);

  boolean canPerformOperation(@NotNull GridSelectionTracker.Operation operation);

  interface Operation {
    boolean checkStackSize(int size);

    boolean checkSelectedColumnsCount(int count);

    boolean checkSelectedRowsCount(int count);

    boolean perform(@NotNull GridSelectionTracker tracker);
  }
}

package com.intellij.ide;

import com.intellij.openapi.actionSystem.DataContext;

public interface PasteProvider {
  void performPaste(DataContext dataContext);

  /**
   * Should perform fast and memory cheap negation. May return incorrect true.
   * See #12326
   */
  boolean isPastePossible(DataContext dataContext);
  boolean isPasteEnabled(DataContext dataContext);
}

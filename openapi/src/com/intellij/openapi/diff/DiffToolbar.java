/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.diff;

import com.intellij.openapi.actionSystem.AnAction;

public interface DiffToolbar {
  /**
   * An action can access diff view via {@link com.intellij.openapi.actionSystem.DataContext}.
   * @see com.intellij.openapi.actionSystem.DataConstants#DIFF_VIEWER
   * @see AnAction#update(com.intellij.openapi.actionSystem.AnActionEvent)
   * @see AnAction#actionPerformed(com.intellij.openapi.actionSystem.AnActionEvent)
   * @see com.intellij.openapi.actionSystem.DataContext
   */ 
  void addAction(AnAction action);
  void addSeparator();

  /**
   * Removes action with specified id.
   * @param actionId id of action to remove
   * @return iff action with specified id was found in toolbar.
   */
  boolean removeActionById(String actionId);
}

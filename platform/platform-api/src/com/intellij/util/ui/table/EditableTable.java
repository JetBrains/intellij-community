// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.table;

import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public interface EditableTable {
  Key<EditableTable> KEY = Key.create("EditableTable");

  /**
   * @param presentation the presentation of visible table.StartEditingAction to configure
   * @param row          the row to be edited
   * @param column       the column to be edited
   */
  void updateAction(@NotNull Presentation presentation, int row, int column);
}

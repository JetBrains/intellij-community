// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actionsOnSave;

import com.intellij.ide.IdeBundle;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

class ActivatedOnColumnInfo extends SameRendererAndEditorColumnInfo<ActionOnSaveInfo> {
  ActivatedOnColumnInfo() {
    super(IdeBundle.message("actions.on.save.table.column.name.activated.on"));
  }

  @Override
  public String getMaxStringValue() {
    // Affects column width
    return "Explicit save (Ctrl + S)  []";
  }

  @Override
  protected @NotNull JComponent getCellComponent(@NotNull TableView<?> table, @NotNull ActionOnSaveInfo info, boolean hovered) {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(JBUI.Borders.empty(6, 8, 0, 0));
    panel.add(info.getActivatedOnComponent(), BorderLayout.NORTH);
    ActionOnSaveColumnInfo.setupTableCellBackground(panel, hovered);

    return panel;
  }
}

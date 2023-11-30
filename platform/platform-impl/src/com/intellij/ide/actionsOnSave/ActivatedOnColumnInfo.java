// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actionsOnSave;

import com.intellij.ide.IdeBundle;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

final class ActivatedOnColumnInfo extends SameRendererAndEditorColumnInfo<ActionOnSaveInfo> {
  ActivatedOnColumnInfo() {
    super(IdeBundle.message("actions.on.save.table.column.name.activated.on"));
  }

  @Override
  public String getMaxStringValue() {
    // Affects column width. Use the longest label + space for drop-down arrow + insets
    return ActionOnSaveInfo.getExplicitSaveText() + "xxxxxx";
  }

  @Override
  protected @NotNull JComponent getCellComponent(@NotNull TableView<?> table, @NotNull ActionOnSaveInfo info, boolean hovered) {
    JComponent component = info.getActivatedOnComponent();
    Dimension size = component.getPreferredSize();
    int baseline = component.getBaseline(size.width, size.height);

    JCheckBox anchorCheckBox = new JCheckBox(info.getActionOnSaveName());
    Dimension cbSize = anchorCheckBox.getPreferredSize();

    int baselineDelta = baseline < 0 ? 0 : anchorCheckBox.getBaseline(cbSize.width, cbSize.height) - baseline;

    JPanel panel = new JPanel(new BorderLayout());
    //noinspection UseDPIAwareBorders  - baselineDelta is already scaled
    panel.setBorder(new EmptyBorder(JBUI.scale(ActionOnSaveColumnInfo.TOP_INSET) + baselineDelta, JBUI.scale(8), 0, 0));
    panel.add(component, BorderLayout.NORTH);
    ActionOnSaveColumnInfo.setupTableCellBackground(panel, hovered);

    return panel;
  }
}

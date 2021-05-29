// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actionsOnSave;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.actionsOnSave.api.ActionOnSaveInfo;
import com.intellij.openapi.ui.panel.ComponentPanelBuilder;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.DropDownLink;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

class ActionOnSaveColumnInfo extends SameRendererAndEditorColumnInfo<ActionOnSaveInfo> {
  ActionOnSaveColumnInfo() {
    super(IdeBundle.message("actions.on.save.table.column.name.action"));
  }

  @Override
  protected @NotNull JComponent getCellComponent(@NotNull ActionOnSaveInfo info, boolean hovered) {
    JPanel resultPanel = new JPanel(new GridBagLayout());
    resultPanel.setBorder(JBUI.Borders.empty(6, 8, 0, 0));

    GridBagConstraints c = new GridBagConstraints();
    c.weightx = 1.0;
    c.weighty = 1.0;
    c.anchor = GridBagConstraints.NORTHWEST;
    c.fill = GridBagConstraints.NONE;

    resultPanel.add(createActionNamePanel(info), c);

    c.weightx = 0.0;
    c.anchor = GridBagConstraints.NORTHEAST;
    c.insets = JBUI.insets(0, 7);

    if (hovered) {
      for (ActionLink actionLink : info.getActionLinks()) {
        resultPanel.add(actionLink, c);
      }
    }

    DropDownLink<?> dropDownLink = info.getInPlaceConfigDropDownLink();
    if (dropDownLink != null) {
      resultPanel.add(dropDownLink, c);
    }

    setupTableCellBackground(resultPanel, hovered);
    return resultPanel;
  }

  private static @NotNull JPanel createActionNamePanel(@NotNull ActionOnSaveInfo info) {
    if (info.isShowCheckbox()) {
      // This anchorCheckBox is not painted and doesn't appear in the UI component hierarchy. Its purpose is to make sure that the preferred
      // size of the real checkBox is calculated correctly. The problem is that com.intellij.ide.ui.laf.darcula.ui.DarculaCheckBoxBorder.getBorderInsets()
      // returns different result for a check box that has CellRendererPane class as its UI ancestor. But we need TableCellEditor and
      // TableCellRenderer to look 100% identically.
      JCheckBox anchorCheckBox = new JCheckBox(info.getActionOnSaveName());

      JBCheckBox checkBox = new JBCheckBox(info.getActionOnSaveName());
      checkBox.setAnchor(anchorCheckBox);

      ComponentPanelBuilder builder = UI.PanelFactory.panel(checkBox);
      if (info.getComment() != null) {
        builder.withComment(info.getComment(), false);
      }

      return builder.createPanel();
    }


    JPanel panel = new JPanel(new GridLayout(2, 1, 0, JBUI.scale(3)));

    // The label should have the same indent as the check box text
    int leftInsetScaled = UIUtil.getCheckBoxTextHorizontalOffset(new JCheckBox(info.getActionOnSaveName())); // already scaled
    //noinspection UseDPIAwareBorders - already scaled
    panel.setBorder(new EmptyBorder(0, leftInsetScaled, 0, 0));

    JBLabel label = new JBLabel(info.getActionOnSaveName());
    // disabled label looks just the same as its comment on Windows, so `setEnabled(false)` is not called for this `label`
    panel.add(label);

    if (info.getComment() != null) {
      panel.add(ComponentPanelBuilder.createCommentComponent(info.getComment(), true, -1, false));
    }

    return panel;
  }

  static void setupTableCellBackground(@NotNull JComponent component, boolean hovered) {
    Color bgColor = hovered ? JBUI.CurrentTheme.Table.Hover.background(true)
                            : UIUtil.getTableBackground(false, false);
    UIUtil.setOpaqueRecursively(component, false);
    component.setOpaque(true);
    component.setBackground(bgColor);
  }
}

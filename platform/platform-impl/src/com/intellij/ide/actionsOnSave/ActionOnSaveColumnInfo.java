// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actionsOnSave;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.ui.panel.ComponentPanelBuilder;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

final class ActionOnSaveColumnInfo extends SameRendererAndEditorColumnInfo<ActionOnSaveInfo> {
  static final int TOP_INSET = 5;

  ActionOnSaveColumnInfo() {
    super(IdeBundle.message("actions.on.save.table.column.name.action"));
  }

  @Override
  protected @NotNull JComponent getCellComponent(@NotNull TableView<?> table, @NotNull ActionOnSaveInfo info, boolean hovered) {
    JPanel resultPanel = new JPanel(new GridBagLayout());
    resultPanel.setBorder(JBUI.Borders.empty(TOP_INSET, 8, 0, 0));

    // This anchorCheckBox is not painted and doesn't appear in the UI component hierarchy. Its purpose is to make sure that the preferred
    // size of the real checkBox is calculated correctly. The problem is that com.intellij.ide.ui.laf.darcula.ui.DarculaCheckBoxBorder.getBorderInsets()
    // returns different result for a checkbox that has CellRendererPane class as its UI ancestor. We need TableCellEditor and
    // TableCellRenderer to look 100% identically.
    // Its second goal is to normalize baseline of other components.
    // The third use case is to calculate checkbox text horizontal offset - needed to align a label if label is used instead of a checkbox.
    JCheckBox anchorCheckBox = new JCheckBox(info.getActionOnSaveName());
    Dimension cbSize = anchorCheckBox.getPreferredSize();
    int anchorBaseline = anchorCheckBox.getBaseline(cbSize.width, cbSize.height);

    GridBagConstraints gbc = new GridBagConstraints();
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    gbc.fill = GridBagConstraints.NONE;

    resultPanel.add(createActionNamePanel(table, info, anchorCheckBox, anchorBaseline), gbc);

    gbc.weightx = 0.0;
    gbc.anchor = GridBagConstraints.NORTHEAST;

    if (hovered) {
      for (ActionLink link : info.getActionLinks()) {
        Dimension linkSize = link.getPreferredSize();
        int baselineDelta = anchorBaseline - link.getBaseline(linkSize.width, linkSize.height);
        //noinspection UseDPIAwareInsets   - baselineDelta is already scaled
        gbc.insets = new Insets(baselineDelta, JBUI.scale(5), 0, JBUI.scale(7));
        resultPanel.add(link, gbc);
      }
    }

    for (JComponent control : info.getDropDownLinks()) {
      Dimension linkSize = control.getPreferredSize();
      int baselineDelta =
        anchorBaseline - control.getBaseline(linkSize.width, linkSize.height);
      //noinspection UseDPIAwareInsets   - baselineDelta is already scaled
      gbc.insets = new Insets(baselineDelta, JBUI.scale(5), 0, JBUI.scale(7));
      resultPanel.add(control, gbc);
    }

    setupTableCellBackground(resultPanel, hovered);
    return resultPanel;
  }

  private static @NotNull JPanel createActionNamePanel(@NotNull TableView<?> table,
                                                       @NotNull ActionOnSaveInfo info,
                                                       @NotNull JCheckBox anchorCheckBox,
                                                       int anchorBaseline) {
    if (info.isSaveActionApplicable()) {
      JBCheckBox checkBox = new JBCheckBox(info.getActionOnSaveName());
      checkBox.setAnchor(anchorCheckBox);

      checkBox.setSelected(info.isActionOnSaveEnabled());
      checkBox.addActionListener(e -> {
        info.setActionOnSaveEnabled(checkBox.isSelected());
        int row = table.getEditingRow();
        int column = table.getEditingColumn();
        if (row >= 0 && column >= 0) {
          // Comment under the checkbox may depend on the checkbox state. Need to re-create the cell editor component.
          table.stopEditing();
          table.editCellAt(row, column);
        }
      });

      ComponentPanelBuilder builder = UI.PanelFactory.panel(checkBox);
      ActionOnSaveComment comment = info.getComment();
      if (comment != null) {
        builder.withComment(comment.getCommentText(), false);
        if (comment.isWarning()) {
          builder.withCommentIcon(AllIcons.General.Warning);
        }
      }

      return builder.createPanel();
    }

    JPanel panel = new JPanel(new GridLayout(2, 1, 0, JBUI.scale(3)));
    JBLabel label = new JBLabel(info.getActionOnSaveName());
    // `setEnabled(false)` is not called for this label because on Windows disabled label looks just the same as its comment

    // The label should have the same indent and baseline as the checkbox text
    int leftInsetScaled = UIUtil.getCheckBoxTextHorizontalOffset(anchorCheckBox); // already scaled

    Dimension labelSize = label.getPreferredSize();
    int baselineDelta = anchorBaseline - label.getBaseline(labelSize.width, labelSize.height);

    //noinspection UseDPIAwareBorders - already scaled
    panel.setBorder(new EmptyBorder(baselineDelta, leftInsetScaled, 0, 0));

    panel.add(label);

    ActionOnSaveComment comment = info.getComment();
    if (comment != null) {
      JLabel commentComponent = ComponentPanelBuilder.createCommentComponent(comment.getCommentText(), true, -1, false);
      if (comment.isWarning()) {
        commentComponent.setIcon(AllIcons.General.Warning);
      }
      panel.add(commentComponent);
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

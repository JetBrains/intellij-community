// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.cellvalidators;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.CellRendererPanel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.function.Supplier;

public class ValidatingTableCellRendererWrapper extends CellRendererPanel implements TableCellRenderer {
  public static final String CELL_VALIDATION_PROPERTY = "CellRenderer.validationInfo";

  private final TableCellRenderer delegate;
  private final JLabel iconLabel = new JLabel();

  private Supplier<? extends Dimension> editorSizeSupplier = () -> JBUI.emptySize();
  private TableCellValidator cellValidator;

  @ApiStatus.Experimental
  public ValidatingTableCellRendererWrapper(TableCellRenderer delegate) {
    this.delegate = delegate;
    setLayout(new BorderLayout(0, 0));
    add(iconLabel, BorderLayout.EAST);

    iconLabel.setOpaque(false);
    setName("Table.cellRenderer");
  }

  @ApiStatus.Experimental
  public ValidatingTableCellRendererWrapper bindToEditorSize(@NotNull Supplier<? extends Dimension> editorSizeSupplier) {
    this.editorSizeSupplier = editorSizeSupplier;
    return this;
  }

  @ApiStatus.Experimental
  public ValidatingTableCellRendererWrapper withCellValidator(@NotNull TableCellValidator cellValidator) {
    this.cellValidator = cellValidator;
    return this;
  }
  @Override
  public Dimension getPreferredSize() {
    Dimension size = super.getPreferredSize();
    size.height = Math.max(size.height, editorSizeSupplier.get().height);
    return size;
  }

  @Override
  public final Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    JComponent delegateRenderer = (JComponent)delegate.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

    if (cellValidator != null) {
      ValidationInfo result = cellValidator.validate(value, row, column);
      iconLabel.setIcon(result == null ? null : result.warning ? AllIcons.General.BalloonWarning : AllIcons.General.BalloonError);
      iconLabel.setBorder(result == null ? null: iconBorder());
      putClientProperty(CELL_VALIDATION_PROPERTY, result);
    }

    add(delegateRenderer, BorderLayout.CENTER);
    setToolTipText(delegateRenderer.getToolTipText());
    setBorder(delegateRenderer.getBorder());
    delegateRenderer.setBorder(null);

    setBackground(delegateRenderer.getBackground());
    return this;
  }

  private static Border iconBorder() {
    return JBUI.Borders.emptyRight(UIUtil.isUnderWin10LookAndFeel() ? 4 : 3);
  }

  @Override
  protected void paintComponent(Graphics g) {
    g.setColor(getBackground());
    g.fillRect(0, 0, getWidth(), getHeight());
  }
}

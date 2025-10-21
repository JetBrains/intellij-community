// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.colors.fileStatus;

import com.intellij.ide.TextCopyProvider;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.util.Collection;
import java.util.Collections;

@ApiStatus.Internal
public final class FileStatusColorsTable extends JBTable implements UiDataProvider {

  public FileStatusColorsTable(@NotNull FileStatusColorsTableModel model) {
    setShowGrid(false);
    setIntercellSpacing(new Dimension(0,0));
    getColumnModel().setColumnSelectionAllowed(false);
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    setDefaultRenderer(FileStatusColorDescriptor.class, new MyDefaultStatusRenderer());
    setDefaultRenderer(String.class, new MyStatusCellRenderer());
    setTableHeader(null);
    setRowHeight(JBUIScale.scale(22));

    setModel(model);
    adjustColumnWidths();
    model.addTableModelListener(this);
  }

  @Override
  public Dimension getPreferredScrollableViewportSize() {
    return new Dimension(JBUIScale.scale(250), getPreferredSize().height);
  }

  private void adjustColumnWidths() {
    for (int col = 0; col < getColumnCount(); col++) {
      DefaultTableColumnModel colModel = (DefaultTableColumnModel) getColumnModel();
      TableColumn column = colModel.getColumn(col);
      Class colClass = getColumnClass(col);
      int width = 0;
      int rightGap = 0;
      if (getColumnClass(col).equals(Boolean.class)) {
        width = JBUIScale.scale(15);
      }
      else {
        rightGap = isColorColumn(col) ? JBUI.size(10, 1).width : 0;
        TableCellRenderer renderer;
        for (int row = 0; row < getRowCount(); row++) {
          renderer = getCellRenderer(row, col);
          Component comp = renderer.getTableCellRendererComponent(this, getValueAt(row, col),
                                                                  false, false, row, col);
          width = Math.max(width, comp.getPreferredSize().width);
        }
      }
      width += rightGap;
      column.setPreferredWidth(width);
      if (colClass.equals(Color.class) || colClass.equals(Boolean.class)) {
        column.setMinWidth(width);
        column.setMaxWidth(width);
      }
    }
  }

  private boolean isColorColumn(int col) {
    return getModel().getColumnClass(col).equals(Color.class);
  }

  public @Nullable FileStatusColorDescriptor getSelectedDescriptor() {
    return ((FileStatusColorsTableModel)getModel()).getDescriptorAt(getSelectedRow());
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    sink.set(PlatformDataKeys.COPY_PROVIDER, new MyCopyProvider());
  }

  private class MyCopyProvider extends TextCopyProvider {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public @Nullable Collection<String> getTextLinesToCopy() {
      FileStatusColorDescriptor descriptor = getSelectedDescriptor();
      if (descriptor == null) return Collections.emptyList();
      return Collections.singletonList(descriptor.getStatus().getText());
    }
  }


  private final class MyStatusCellRenderer extends DefaultTableCellRenderer {

    private final JLabel myLabel = new JLabel();

    MyStatusCellRenderer() {
      myLabel.setOpaque(true);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      if (value instanceof String) {
        FileStatusColorDescriptor descriptor = ((FileStatusColorsTableModel)getModel()).getDescriptorByName((String)value);
        if (descriptor != null) {
          myLabel.setText((String)value);
          myLabel.setForeground(isSelected ? UIUtil.getTableSelectionForeground(true) : descriptor.getColor());
          myLabel.setBackground(UIUtil.getTableBackground(isSelected));
          return myLabel;
        }
      }
      return c;
    }
  }

  private static final class MyDefaultStatusRenderer extends DefaultTableCellRenderer {
    private final JLabel myLabel = new JLabel();
    private final Color myLabelColor;

    MyDefaultStatusRenderer() {
      myLabel.setOpaque(true);
      myLabelColor = ColorUtil.withAlpha(myLabel.getForeground(), 0.5);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      if (value instanceof FileStatusColorDescriptor descriptor) {
        myLabel.setForeground(isSelected ? UIUtil.getTableSelectionForeground(true) : myLabelColor);
        myLabel.setBackground(UIUtil.getTableBackground(isSelected));

        String text = descriptor.getUiThemeColor() != null ? "!" : descriptor.isDefault() ? "" : "*";
        myLabel.setText(text);
        myLabel.setHorizontalAlignment(SwingConstants.CENTER);
        return myLabel;
      }
      return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
    }
  }
}

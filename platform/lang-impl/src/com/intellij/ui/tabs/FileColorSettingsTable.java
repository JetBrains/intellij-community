/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.ui.tabs;

import com.intellij.ui.FileColorManager;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.openapi.ui.StripeTable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author spleaner
 */
public abstract class FileColorSettingsTable extends StripeTable {
  private static final int NAME_COLUMN = 0;
  private static final int COLOR_COLUMN = 1;

  private final List<FileColorConfiguration> myOriginal;

  public FileColorSettingsTable(@NotNull final FileColorManager manager, @NotNull final List<FileColorConfiguration> configurations) {
    super(new ModelAdapter(copy(configurations)));

    myOriginal = configurations;

    setAutoResizeMode(AUTO_RESIZE_LAST_COLUMN);

    final TableColumnModel columnModel = getColumnModel();
    final TableColumn nameColumn = columnModel.getColumn(NAME_COLUMN);
    nameColumn.setCellRenderer(new ScopeNameRenderer());

    final TableColumn colorColumn = columnModel.getColumn(COLOR_COLUMN);
    colorColumn.setCellRenderer(new ColorCellRenderer(manager));
  }

  private static List<FileColorConfiguration> copy(@NotNull final List<FileColorConfiguration> configurations) {
    final List<FileColorConfiguration> result = new ArrayList<FileColorConfiguration>();
    for (FileColorConfiguration c : configurations) {
      try {
        result.add(c.clone());
      }
      catch (CloneNotSupportedException e) {
        assert false : "Should not happen!";
      }
    }

    return result;
  }

  protected abstract void apply(@NotNull final List<FileColorConfiguration> configurations);

  public ModelAdapter getModel() {
    return (ModelAdapter) super.getModel();
  }

  public boolean isModified() {
    final List<FileColorConfiguration> current = getModel().getConfigurations();

    if (myOriginal.size() != current.size()) {
      return true;
    }

    for (int i = 0; i < current.size(); i++) {
      if (!myOriginal.get(i).equals(current.get(i))) {
        return true;
      }
    }

    return false;
  }

  public void reset() {
    getModel().setConfigurations(myOriginal);
  }

  public void performRemove() {
    final int rowCount = getSelectedRowCount();
    if (rowCount > 0) {
      final int[] rows = getSelectedRows();
      for (int i = rows.length - 1; i >= 0; i--) {
        removeConfiguration(rows[i]);
      }
    }
  }

  public void moveUp() {
    final int rowCount = getSelectedRowCount();
    if (rowCount == 1) {
      final int index = getModel().moveUp(getSelectedRows()[0]);
      if (index > -1) {
        getSelectionModel().setSelectionInterval(index, index);
      }
    }
  }

  public void moveDown() {
    final int rowCount = getSelectedRowCount();
    if (rowCount == 1) {
      final int index = getModel().moveDown(getSelectedRows()[0]);
      if (index > -1) {
        getSelectionModel().setSelectionInterval(index, index);
      }
    }
  }

  public void apply() {
    if (isModified()) {
      apply(getModel().getConfigurations());
    }
  }

  public FileColorConfiguration removeConfiguration(final int index) {
    final FileColorConfiguration removed = getModel().remove(index);

    final int rowCount = getRowCount();
    if (rowCount > 0) {
      if (index > rowCount - 1) {
        getSelectionModel().setSelectionInterval(rowCount - 1, rowCount - 1);
      } else {
        getSelectionModel().setSelectionInterval(index, index);
      }
    }

    return removed;
  }

  public void addConfiguration(@NotNull final FileColorConfiguration configuration) {
    getModel().add(configuration);
  }

  private static class ModelAdapter extends AbstractTableModel {
    private List<FileColorConfiguration> myConfigurations;

    private ModelAdapter(final List<FileColorConfiguration> configurations) {
      myConfigurations = configurations;
    }

    @Override
    public String getColumnName(int column) {
      return column == NAME_COLUMN ? "Scope" : "Color";
    }

    public int getRowCount() {
      return myConfigurations.size();
    }

    public int getColumnCount() {
      return 2;
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
      return myConfigurations.get(rowIndex);
    }

    @NotNull
    public List<FileColorConfiguration> getConfigurations() {
      return myConfigurations;
    }

    public FileColorConfiguration remove(int index) {
      final FileColorConfiguration removed = myConfigurations.remove(index);
      fireTableRowsDeleted(index, index);
      return removed;
    }

    public void add(@NotNull final FileColorConfiguration configuration) {
      myConfigurations.add(configuration);
      fireTableRowsInserted(myConfigurations.size() - 1, myConfigurations.size() - 1);
    }

    public void setConfigurations(List<FileColorConfiguration> original) {
      myConfigurations = copy(original);
      fireTableDataChanged();
    }

    public int moveUp(int index) {
      if (index > 0) {
        final FileColorConfiguration configuration = myConfigurations.get(index);
        myConfigurations.remove(index);
        myConfigurations.add(index - 1, configuration);
        fireTableRowsUpdated(index - 1, index);
        return index - 1;
      }

      return -1;
    }

    public int moveDown(int index) {
      if (index < getRowCount() - 1) {
        final FileColorConfiguration configuration = myConfigurations.get(index);
        myConfigurations.remove(index);
        myConfigurations.add(index + 1, configuration);
        fireTableRowsUpdated(index, index + 1);
        return index + 1;
      }

      return -1;
    }
  }

  private static class ScopeNameRenderer extends JLabel implements TableCellRenderer {
    private static final Border NO_FOCUS_BORDER = BorderFactory.createEmptyBorder(1, 1, 1, 1);

    private ScopeNameRenderer() {
      setOpaque(true);
    }

    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      if (!(value instanceof FileColorConfiguration)) {
        return this;
      }

      preinit(table, isSelected, hasFocus);

      final FileColorConfiguration configuration = (FileColorConfiguration)value;
      setText(configuration.getScopeName());
      return this;
    }

    protected void preinit(JTable table, boolean isSelected, boolean hasFocus) {
      setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
      setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());

      setBorder(hasFocus ? (UIManager.getBorder("Table.focusCellHighlightBorder"))
                         : NO_FOCUS_BORDER);
    }
  }

  private static class ColorCellRenderer extends ScopeNameRenderer {
    private Color myColor;
    private final FileColorManager myManager;

    private ColorCellRenderer(final FileColorManager manager) {
      setOpaque(true);

      myManager = manager;

      setIcon(new EmptyIcon(16));
    }

    private void setIconColor(final Color color) {
      myColor = color;
    }

    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      if (!(value instanceof FileColorConfiguration)) {
        return this;
      }

      preinit(table, isSelected, hasFocus);

      final FileColorConfiguration configuration = (FileColorConfiguration)value;
      setIconColor(myManager.getColor(configuration.getColorName()));
      setText(configuration.getColorName());

      return this;
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);

      if (myColor != null) {
        final Icon icon = getIcon();
        final int width = icon.getIconWidth();
        final int height = icon.getIconHeight();

        final Color old = g.getColor();

        g.setColor(myColor);
        g.fillRect(0, 0, width, height);

        g.setColor(old);
      }
    }
  }
}

/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.application.options.colors.fileStatus;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.ui.JBMenuItem;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.ui.ClickListener;
import com.intellij.ui.ColorPicker;
import com.intellij.ui.Gray;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

public class FileStatusColorsTable extends JBTable {

  private JBPopupMenu mySetColorMenu;
  private JBMenuItem myResetItem;

  public FileStatusColorsTable(@NotNull Color defaultColor) {
    setShowGrid(false);
    getColumnModel().setColumnSelectionAllowed(false);
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    setDefaultRenderer(Color.class, new MyColorCellRenderer(defaultColor));
    setTableHeader(null);
    registerKeyboardAction(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          setColor();
        }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, false), JComponent.WHEN_FOCUSED);
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent event, int clickCount) {
        int col = FileStatusColorsTable.this.columnAtPoint(event.getPoint());
        return isColorColumn(col) && setColor();
      }
    }.installOn(this);
    initPopup();
  }

  private void initPopup() {
    mySetColorMenu = new JBPopupMenu();
    mySetColorMenu.add(new JBMenuItem(new ChooseColorAction()));
    mySetColorMenu.add(new JBMenuItem(new DropColorAction()));
    myResetItem = new JBMenuItem(new ResetToDefaultAction());
    mySetColorMenu.add(myResetItem);
  }

  private class ChooseColorAction extends AbstractAction {
    public ChooseColorAction() {
      super(ApplicationBundle.message("file.status.color.menu.choose.color"));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      editColor();
    }
  }

  private class DropColorAction extends AbstractAction {
    public DropColorAction() {
      super(ApplicationBundle.message("file.status.color.menu.normal.text"));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      int row = getSelectedRow();
      if (row >= 0) {
        getModel().setValueAt(null, row, 1);
      }
    }
  }

  private class ResetToDefaultAction extends AbstractAction {
    public ResetToDefaultAction() {
      super(ApplicationBundle.message("file.status.color.menu.reset.to.default"));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      int row = getSelectedRow();
      if (row >= 0) {
        ((FileStatusColorsTableModel)getModel()).resetToDefault(row);
      }
    }
  }

  private boolean setColor() {
    Point location = getPopupLocation();
    if (location != null) {
      mySetColorMenu.show(this, location.x, location.y);
      myResetItem.setVisible(isResetAvailable());
      mySetColorMenu.pack();
      mySetColorMenu.getSelectionModel().setSelectedIndex(0);
      return true;
    }
    return false;
  }

  private boolean isResetAvailable() {
    int row = getSelectedRow();
    return row >= 0 && ((FileStatusColorsTableModel)getModel()).isResetAvailable(row);
  }

  @Nullable
  private Point getPopupLocation() {
    int row = getSelectedRow();
    if (row >= 0) {
      Rectangle cellRect = getCellRect(row, 0, false);
      return cellRect.getLocation();
    }
    return null;
  }

  private void editColor() {
    int row = getSelectedRow();
    if (row >= 0) {
      int colorColumn = getColumn(Color.class);
      Color currentColor = (Color)getModel().getValueAt(row, colorColumn);
      Color color = ColorPicker.showDialog(this, ApplicationBundle.message("title.file.status.color"), currentColor, true, null, false);
      if (color != null) {
        getModel().setValueAt(color, row, colorColumn);
      }
    }
  }

  public void adjustColumnWidths() {
    for (int col = 0; col < getColumnCount(); col++) {
      int rightGap = isColorColumn(col) ? JBUI.size(10, 1).width : 0;
      DefaultTableColumnModel colModel = (DefaultTableColumnModel) getColumnModel();
      TableColumn column = colModel.getColumn(col);
      int width = 0;

      TableCellRenderer renderer;
      for (int row = 0; row < getRowCount(); row++) {
        renderer = getCellRenderer(row, col);
        Component comp = renderer.getTableCellRendererComponent(this, getValueAt(row, col),
            false, false, row, col);
        width = Math.max(width, comp.getPreferredSize().width);
      }
      width += rightGap;
      column.setPreferredWidth(width);
      if (isColorColumn(col)) {
        column.setMinWidth(width);
        column.setMaxWidth(width);
      }
    }
  }

  private boolean isColorColumn(int col) {
    return getModel().getColumnClass(col).equals(Color.class);
  }

  private int getColumn(@NotNull Class columnClass) {
    for (int i = 0; i < getModel().getColumnCount(); i ++) {
      if (getModel().getColumnClass(i).equals(columnClass)) return i;
    }
    return -1;
  }

  private static class MyColorCellRenderer implements TableCellRenderer {
    public static final int RIGHT_GAP = 10;
    private Color myDefaultColor;

    public MyColorCellRenderer(@NotNull Color defaultColor) {
      myDefaultColor = defaultColor;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      JLabel colorLabel = new JLabel();
      Color c = getDisplayColor(value);
      colorLabel.setBorder(new JBEmptyBorder(0, RIGHT_GAP, 0, 0));
      colorLabel.setIcon(getIcon(c));
      //noinspection StringToUpperCaseOrToLowerCaseWithoutLocale
      colorLabel.setText(value != null ? "" : ApplicationBundle.message("file.status.color.none"));
      colorLabel.setForeground(c);
      if (isSelected) {
        colorLabel.setOpaque(true);
        colorLabel.setForeground(UIUtil.getTableSelectionForeground());
        colorLabel.setBackground(UIUtil.getTableSelectionBackground());
      }
      return colorLabel;
    }

    @NotNull
    private Color getDisplayColor(@Nullable Object value) {
      return value instanceof Color ? (Color)value : myDefaultColor;
    }

    private Icon getIcon(@NotNull  Color color) {
      return color != myDefaultColor ? JBUI.scale(new MyColorIcon(color)) : null;
    }
  }

  private static class MyColorIcon extends EmptyIcon {

    public static final int COLOR_HEIGHT = 14;
    public static final int ICON_HEIGHT = 16;
    public static final int ICON_WIDTH = 32;
    private Color myColor;

    public MyColorIcon(@NotNull Color color) {
      //noinspection deprecation
      super(ICON_WIDTH, ICON_HEIGHT);
      myColor = color;
    }

    @Override
    public void paintIcon(final Component component, final Graphics g, final int i, final int j) {
      final int iconHeight = getIconHeight();
      final int iconWidth = getIconWidth();
      g.setColor(myColor);

      final int size = scaleVal(COLOR_HEIGHT);
      final int y = j + (iconHeight - size) / 2;

      g.fillRect(i, y, iconWidth, size);

      g.setColor(Gray.x00.withAlpha(40));
      g.drawRect(i, y, iconWidth, size);
    }

  }
}

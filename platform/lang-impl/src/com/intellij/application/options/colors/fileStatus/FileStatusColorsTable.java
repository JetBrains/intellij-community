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
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.ColorIcon;
import com.intellij.util.ui.EmptyIcon;
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

  public FileStatusColorsTable(@NotNull Color defaultColor) {
    setShowGrid(false);
    getColumnModel().setColumnSelectionAllowed(false);
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    setDefaultRenderer(Color.class, new MyColorCellRenderer(defaultColor));
    registerKeyboardAction(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          setColor();
        }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, false), JComponent.WHEN_FOCUSED);
    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent event) {
        return setColor();
      }
    }.installOn(this);
    initPopup();
  }

  private void initPopup() {
    mySetColorMenu = new JBPopupMenu();
    mySetColorMenu.add(new JBMenuItem(new ChooseColorAction()));
    mySetColorMenu.add(new JBMenuItem(new ResetToDefaultAction()));
    mySetColorMenu.add(new JBMenuItem(new DropColorAction()));
  }

  private class ChooseColorAction extends AbstractAction {
    public ChooseColorAction() {
      super("Choose Color...");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      editColor();
    }
  }

  private class DropColorAction extends AbstractAction {
    public DropColorAction() {
      super("Set to Normal Text");
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
      super("Reset to Default");
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
      mySetColorMenu.getSelectionModel().setSelectedIndex(0);
      return true;
    }
    return false;
  }

  @Nullable
  private Point getPopupLocation() {
    int row = getSelectedRow();
    if (row >= 0) {
      Rectangle cellRect = getCellRect(row, 1, false);
      return cellRect.getLocation();
    }
    return null;
  }

  private void editColor() {
    int row = getSelectedRow();
    if (row >= 0) {
      Color currentColor = (Color)getModel().getValueAt(row, 1);
      Color color = ColorPicker.showDialog(this, ApplicationBundle.message("title.file.status.color"), currentColor, true, null, false);
      if (color != null) {
        getModel().setValueAt(color, row, 1);
      }
    }
  }

  public void adjustColumnWidths() {
    for (int col = 0; col < getColumnCount(); col++) {
      int rightGap = col > 0 ? JBUI.size(10,1).width : 0;
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
      if (col > 0) {
        column.setMinWidth(width);
        column.setMaxWidth(width);
      }
    }
  }

  private static class MyColorCellRenderer implements TableCellRenderer {
    private Color myDefaultColor;

    public MyColorCellRenderer(@NotNull Color defaultColor) {
      myDefaultColor = defaultColor;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      JLabel colorLabel = new JLabel();
      Color c = getDisplayColor(value);
      colorLabel.setIcon(getIcon(c));
      //noinspection StringToUpperCaseOrToLowerCaseWithoutLocale
      colorLabel.setText(value != null ? ColorUtil.toHex(c).toUpperCase() : ApplicationBundle.message("file.status.color.none"));
      colorLabel.setForeground(c);
      if (isSelected) {
        colorLabel.setOpaque(true);
        colorLabel.setForeground(UIUtil.getTableSelectionForeground());
        colorLabel.setBackground(UIUtil.getTableSelectionBackground());
      }
      return colorLabel;
    }

    private Color getDisplayColor(@Nullable Object value) {
      return value instanceof Color ? (Color)value : myDefaultColor;
    }

    private static Icon getIcon(Color color) {
      return color == null ? EmptyIcon.ICON_16 : JBUI.scale(new ColorIcon(16, 13, color, true));
    }
  }
}

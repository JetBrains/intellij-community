// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.colors.fileStatus;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.ui.ColorPanel;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class FileStatusColorsPanel {

  private static final int TABLE_SIZE = 250; // Defined by UI spec

  private JPanel myTopPanel;
  private JBTable myFileStatusColorsTable;
  private JBCheckBox myFileStatusColorBox;
  private JButton myRestoreButton;
  private ColorPanel myColorPanel;
  private JBScrollPane myTablePane;
  private JPanel myColorSettingsPanel;
  private JLabel myCustomizedLabel;
  private final FileStatusColorsTableModel myModel;

  public FileStatusColorsPanel(FileStatus @NotNull [] fileStatuses) {
    myModel = new FileStatusColorsTableModel(fileStatuses, getCurrentScheme());
    myFileStatusColorsTable.setModel(
      myModel);
    ((FileStatusColorsTable)myFileStatusColorsTable).adjustColumnWidths();
    myModel.addTableModelListener(myFileStatusColorsTable);
    myFileStatusColorsTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        updateColorPanel(myModel.getDescriptorAt(myFileStatusColorsTable.getSelectedRow()));
      }
    });
    myRestoreButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        restoreDefault(myFileStatusColorsTable.getSelectedRow());
      }
    });
    myFileStatusColorBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        setUseColor(myFileStatusColorsTable.getSelectedRow(), myFileStatusColorBox.isSelected());
      }
    });
    myColorPanel.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        //noinspection ConstantConditions
        setColor(myFileStatusColorsTable.getSelectedRow(), myColorPanel.getSelectedColor());
      }
    });
    adjustTableSize();
    myColorSettingsPanel.setVisible(false);
    updateCustomizedLabel();
    myModel.addTableModelListener(new TableModelListener() {
      @Override
      public void tableChanged(TableModelEvent e) {
        updateCustomizedLabel();
      }
    });
  }

  private void updateCustomizedLabel() {
    boolean isVisible = myModel.containsCustomSettings();
    myCustomizedLabel.setForeground(isVisible ? JBColor.GRAY : UIUtil.getLabelBackground());
  }

  private void adjustTableSize() {
    Dimension d = myFileStatusColorsTable.getPreferredSize();
    d.setSize(JBUIScale.scale(TABLE_SIZE), d.height);
    myTablePane.setMinimumSize(new Dimension(JBUIScale.scale(TABLE_SIZE), 0));
    myTablePane.setPreferredSize(d);
    myTablePane.setMaximumSize(d);
  }

  public JPanel getComponent() {
    SwingUtilities.updateComponentTreeUI(myTopPanel); // TODO: create Swing components in this method (see javadoc)
    return myTopPanel;
  }

  private void createUIComponents() {
    myFileStatusColorsTable = new FileStatusColorsTable();
  }

  @NotNull
  private static EditorColorsScheme getCurrentScheme() {
    return EditorColorsManager.getInstance().getSchemeForCurrentUITheme();
  }

  @NotNull
  public FileStatusColorsTableModel getModel() {
    return myModel;
  }

  private void updateColorPanel(@Nullable FileStatusColorDescriptor descriptor) {
    if (descriptor == null) {
       myColorSettingsPanel.setVisible(false);
    }
    else {
      myColorSettingsPanel.setVisible(true);
      myFileStatusColorBox.setSelected(descriptor.getColor() != null);
      myRestoreButton.setEnabled(!descriptor.isDefault());
      myColorPanel.setSelectedColor(descriptor.getColor());
    }
  }

  private void restoreDefault(int row) {
    if (row >= 0) {
      myModel.resetToDefault(row);
      updateColorPanel(myModel.getDescriptorAt(row));
    }
  }

  private void setUseColor(int row, boolean useColor) {
    if (row >= 0) {
      FileStatusColorDescriptor descriptor = myModel.getDescriptorAt(row);
      if (descriptor != null) {
        Color defaultColor = descriptor.getDefaultColor();
        Color c = useColor ? defaultColor != null ? defaultColor : UIUtil.getLabelForeground() : null;
        getModel().setValueAt(c, row, 1);
        updateColorPanel(descriptor);
      }
    }
  }

  private void setColor(int row, @NotNull Color color) {
    if (row  >= 0) {
      myModel.setValueAt(color, row, 1);
      FileStatusColorDescriptor descriptor = myModel.getDescriptorAt(row);
      if (descriptor != null) {
        updateColorPanel(descriptor);
      }
    }
  }
}

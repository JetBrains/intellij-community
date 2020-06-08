// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.wm.IdeFocusManager;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Konstantin Bulenkov
 */
public final class EditableRowTable{
  private EditableRowTable() {
  }

  public static JPanel createButtonsTable(final JTable table, final RowEditableTableModel tableModel, boolean addMnemonics) {
    JPanel buttonsPanel = new JPanel();
    buttonsPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

    buttonsPanel.setLayout(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.HORIZONTAL;
    gbConstraints.insets = new Insets(2, 4, 2, 4);

    final JButton addButton = new JButton();
    addButton.setText(addMnemonics ?
                        UIBundle.message("row.add") :
                        UIBundle.message("row.add.without.mnemonic"));
    addButton.setDefaultCapable(false);
    buttonsPanel.add(addButton, gbConstraints);

    final JButton removeButton = new JButton();
    removeButton.setText(addMnemonics ?
                           UIBundle.message("row.remove") :
                           UIBundle.message("row.remove.without.mnemonic"));
    removeButton.setDefaultCapable(false);
    buttonsPanel.add(removeButton, gbConstraints);

    final JButton upButton = new JButton();
    upButton.setText(addMnemonics ?
                       UIBundle.message("row.move.up") :
                       UIBundle.message("row.move.up.without.mnemonic"));
    upButton.setDefaultCapable(false);
    buttonsPanel.add(upButton, gbConstraints);

    final JButton downButton = new JButton();
    downButton.setText(addMnemonics ?
                         UIBundle.message("row.move.down") :
                         UIBundle.message("row.move.down.without.mnemonic"));
    downButton.setDefaultCapable(false);
    buttonsPanel.add(downButton, gbConstraints);

    gbConstraints.weighty = 1;
    buttonsPanel.add(new JPanel(), gbConstraints);

    addButton.addActionListener(
      new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          TableUtil.stopEditing(table);
          tableModel.addRow();
          final int index = tableModel.getRowCount() - 1;
          table.editCellAt(index, 0);
          table.setRowSelectionInterval(index, index);
          table.setColumnSelectionInterval(0, 0);
          table.getParent().repaint();
          final Component editorComponent = table.getEditorComponent();
          if (editorComponent != null) {
            final Rectangle bounds = editorComponent.getBounds();
            table.scrollRectToVisible(bounds);
            IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(editorComponent, true));
          }
        }
      }
    );

    removeButton.addActionListener(
      new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          TableUtil.stopEditing(table);
          int index = table.getSelectedRow();
          if (0 <= index && index < tableModel.getRowCount()) {
            tableModel.removeRow(index);
            if (index < tableModel.getRowCount()) {
              table.setRowSelectionInterval(index, index);
            }
            else {
              if (index > 0) {
                table.setRowSelectionInterval(index - 1, index - 1);
              }
            }
            updateButtons(table, tableModel, addButton, removeButton, upButton, downButton);
          }

          table.getParent().repaint();
          IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(table, true));
        }
      }
    );

    upButton.addActionListener(
      new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          TableUtil.stopEditing(table);
          int index = table.getSelectedRow();
          if (0 < index && index < tableModel.getRowCount()) {
            tableModel.exchangeRows(index, index - 1);
            table.setRowSelectionInterval(index - 1, index - 1);
          }
          IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(table, true));
        }
      }
    );

    downButton.addActionListener(
      new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          TableUtil.stopEditing(table);
          int index = table.getSelectedRow();
          if (0 <= index && index < tableModel.getRowCount() - 1) {
            tableModel.exchangeRows(index, index + 1);
            table.setRowSelectionInterval(index + 1, index + 1);
          }
          IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(table, true));
        }
      }
    );

    table.getSelectionModel().addListSelectionListener(
      new ListSelectionListener() {
        @Override
        public void valueChanged(ListSelectionEvent e) {
          updateButtons(table, tableModel, addButton, removeButton, upButton, downButton);
        }
      }
    );
    updateButtons(table, tableModel, addButton, removeButton, upButton, downButton);

    return buttonsPanel;
  }

  private static void updateButtons(JTable table, final RowEditableTableModel tableModel,
                                    final JButton addButton,
                                    final JButton removeButton,
                                    final JButton upButton,
                                    final JButton downButton) {
    if (table.isEnabled()) {
      int index = table.getSelectedRow();
      if (0 <= index && index < tableModel.getRowCount()) {
        removeButton.setEnabled(true);
        upButton.setEnabled(index > 0);
        downButton.setEnabled(index < tableModel.getRowCount() - 1);
      }
      else {
        removeButton.setEnabled(false);
        upButton.setEnabled(false);
        downButton.setEnabled(false);
      }

      addButton.setEnabled(true);
    }
  }
}

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
package com.intellij.ui;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.util.Ref;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Konstantin Bulenkov
 */
public class EditableRowTable {
  private EditableRowTable() {}

  public static JPanel createButtonsTable(final JTable table, final RowEditableTableModel tableModel, boolean addMnemonics) {
    return createButtonsTable(table, tableModel, addMnemonics, true, true);
  }

  public static JPanel createButtonsTable(final JTable table, final RowEditableTableModel tableModel,
                                          boolean addMnemonics, boolean iconsOnly, boolean isHorizontal,
                                          AnAction...actions) {
    JPanel panel = new JPanel();
    panel.setBorder(iconsOnly ? IdeBorderFactory.createEmptyBorder(0) : BorderFactory.createEmptyBorder(4, 4, 4, 4));

    panel.setLayout(iconsOnly ? new BorderLayout() : new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.HORIZONTAL;
    gbConstraints.insets = new Insets(2, 4, 2, 4);
    final Ref<JButton> addButton = Ref.create(null);
    final Ref<JButton> removeButton = Ref.create(null);
    final Ref<JButton> upButton = Ref.create(null);
    final Ref<JButton> downButton = Ref.create(null);
    final Ref<AddRemoveUpDownPanel> p = Ref.create(null);

    final AddRemoveUpDownPanel.Listener listener = new AddRemoveUpDownPanel.Listener() {
      @Override
      public void doAdd() {
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
          editorComponent.requestFocus();
        }
      }

      @Override
      public void doRemove() {
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
          updateButtons(table, tableModel, addButton.get(), removeButton.get(), upButton.get(), downButton.get(), p.get());
        }

        table.getParent().repaint();
        table.requestFocus();
      }

      @Override
      public void doUp() {
        TableUtil.stopEditing(table);
        int index = table.getSelectedRow();
        if (0 < index && index < tableModel.getRowCount()) {
          tableModel.exchangeRows(index, index - 1);
          table.setRowSelectionInterval(index - 1, index - 1);
        }
        table.requestFocus();
      }

      @Override
      public void doDown() {
          TableUtil.stopEditing(table);
          int index = table.getSelectedRow();
          if (0 <= index && index < tableModel.getRowCount() - 1) {
            tableModel.exchangeRows(index, index + 1);
            table.setRowSelectionInterval(index + 1, index + 1);
          }
          table.requestFocus();
      }
    };

    if (!iconsOnly) {
      addButton.set(new JButton());
      addButton.get().setText(addMnemonics ? UIBundle.message("row.add") : UIBundle.message("row.add.without.mnemonic"));
      addButton.get().setDefaultCapable(false);
      panel.add(addButton.get(), gbConstraints);

      removeButton.set(new JButton());
      removeButton.get().setText(addMnemonics ? UIBundle.message("row.remove") : UIBundle.message("row.remove.without.mnemonic"));
      removeButton.get().setDefaultCapable(false);
      panel.add(removeButton.get(), gbConstraints);

      upButton.set(new JButton());
      upButton.get().setText(addMnemonics ? UIBundle.message("row.move.up") : UIBundle.message("row.move.up.without.mnemonic"));
      upButton.get().setDefaultCapable(false);
      panel.add(upButton.get(), gbConstraints);

      downButton.set(new JButton());
      downButton.get().setText(addMnemonics ? UIBundle.message("row.move.down") : UIBundle.message("row.move.down.without.mnemonic"));
      downButton.get().setDefaultCapable(false);
      panel.add(downButton.get(), gbConstraints);

      addButton.get().addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          listener.doAdd();
        }
      });

      removeButton.get().addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          listener.doRemove();
        }
      });

      upButton.get().addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          listener.doUp();
        }
      });

      downButton.get().addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          listener.doDown();
        }
      });
      gbConstraints.weighty = 1;
      panel.add(new JPanel(), gbConstraints);
    } else {
      p.set(new AddRemoveUpDownPanel(listener, table, isHorizontal, actions, AddRemoveUpDownPanel.Buttons.ALL));
      panel.add(p.get(), BorderLayout.NORTH);
    }

    table.getSelectionModel().addListSelectionListener(
      new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent e) {
          updateButtons(table, tableModel, addButton.get(), removeButton.get(), upButton.get(), downButton.get(), p.get());
        }
      }
    );
    updateButtons(table, tableModel, addButton.get(), removeButton.get(), upButton.get(), downButton.get(), p.get());
    return panel;
  }

  private static void updateButtons(final JTable table,
                                    final RowEditableTableModel tableModel,
                                    final JButton add,
                                    final JButton remove,
                                    final JButton up,
                                    final JButton down,
                                    final AddRemoveUpDownPanel p) {
    if (table.isEnabled()) {
      final int index = table.getSelectedRow();
      if (0 <= index && index < tableModel.getRowCount()) {
        final boolean downEnable = index < tableModel.getRowCount() - 1;
        final boolean upEnable = index > 0;
        if (p != null) {
          p.setEnabled(AddRemoveUpDownPanel.Buttons.REMOVE, true);
          p.setEnabled(AddRemoveUpDownPanel.Buttons.UP, upEnable);
          p.setEnabled(AddRemoveUpDownPanel.Buttons.DOWN, downEnable);
        } else {
          remove.setEnabled(true);
          up.setEnabled(upEnable);
          down.setEnabled(downEnable);
        }
      } else {
        if (p != null) {
          p.setEnabled(AddRemoveUpDownPanel.Buttons.REMOVE, false);
          p.setEnabled(AddRemoveUpDownPanel.Buttons.UP, false);
          p.setEnabled(AddRemoveUpDownPanel.Buttons.DOWN, false);
        } else {
          remove.setEnabled(false);
          up.setEnabled(false);
          down.setEnabled(false);
        }
      }
      if (p != null) {
        p.setEnabled(AddRemoveUpDownPanel.Buttons.ADD, true);
      } else {
        add.setEnabled(true);
      }
    }
  }
}

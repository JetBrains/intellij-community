/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.application.options;

import com.intellij.ide.highlighter.JavaHighlightingColors;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.codeStyle.PackageEntry;
import com.intellij.psi.codeStyle.PackageEntryTable;
import com.intellij.ui.*;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.table.JBTable;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * @author Max Medvedev
 */
public abstract class ImportLayoutPanel extends JPanel {
  private final JBCheckBox myCbLayoutStaticImportsSeparately = new JBCheckBox("Layout static imports separately");
  private final JBTable myImportLayoutTable;

  private final PackageEntryTable myImportLayoutList = new PackageEntryTable();

  public JBTable getImportLayoutTable() {
    return myImportLayoutTable;
  }

  public PackageEntryTable getImportLayoutList() {
    return myImportLayoutList;
  }

  public JBCheckBox getCbLayoutStaticImportsSeparately() {
    return myCbLayoutStaticImportsSeparately;
  }

  public ImportLayoutPanel() {
    super(new BorderLayout());
    setBorder(IdeBorderFactory.createTitledBorder(ApplicationBundle.message("title.import.layout"), false, new Insets(0, 0, 0, 0)));

    myCbLayoutStaticImportsSeparately.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        if (areStaticImportsEnabled()) {
          boolean found = false;
          for (int i = myImportLayoutList.getEntryCount() - 1; i >= 0; i--) {
            PackageEntry entry = myImportLayoutList.getEntryAt(i);
            if (entry == PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY) {
              found = true;
              break;
            }
          }
          if (!found) {
            int index = myImportLayoutList.getEntryCount();
            if (index != 0 && myImportLayoutList.getEntryAt(index - 1) != PackageEntry.BLANK_LINE_ENTRY) {
              myImportLayoutList.addEntry(PackageEntry.BLANK_LINE_ENTRY);
            }
            myImportLayoutList.addEntry(PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY);
          }
        }
        else {
          for (int i = myImportLayoutList.getEntryCount() - 1; i >= 0; i--) {
            PackageEntry entry = myImportLayoutList.getEntryAt(i);
            if (entry.isStatic()) {
              myImportLayoutList.removeEntryAt(i);
            }
          }
        }
        refresh();
      }
    });
    
    add(myCbLayoutStaticImportsSeparately, BorderLayout.NORTH);
    
    JPanel importLayoutPanel = ToolbarDecorator.createDecorator(myImportLayoutTable = createTableForPackageEntries(myImportLayoutList, this))
      .addExtraAction(new DumbAwareActionButton(ApplicationBundle.message("button.add.package"), IconUtil.getAddPackageIcon()) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          addPackageToImportLayouts();
        }

        @Override
        public ShortcutSet getShortcut() {
          return CommonShortcuts.getNewForDialogs();
        }
      })
      .addExtraAction(new DumbAwareActionButton(ApplicationBundle.message("button.add.blank"), IconUtil.getAddBlankLineIcon()) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          addBlankLine();
        }
      })
      .setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          removeEntryFromImportLayouts();
        }
      })
      .setMoveUpAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          moveRowUp();
        }
      })
      .setMoveDownAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          moveRowDown();
        }
      })
      .setRemoveActionUpdater(new AnActionButtonUpdater() {
        @Override
        public boolean isEnabled(AnActionEvent e) {
          int selectedImport = myImportLayoutTable.getSelectedRow();
          PackageEntry entry = selectedImport < 0 ? null : myImportLayoutList.getEntryAt(selectedImport);
          return entry != null && entry != PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY && entry != PackageEntry.ALL_OTHER_IMPORTS_ENTRY;
        }
      })
      .setButtonComparator(ApplicationBundle.message("button.add.package"), ApplicationBundle.message("button.add.blank"), "Remove", "Up", "Down")
      .setPreferredSize(new Dimension(-1, 100)).createPanel();
    
    
    add(importLayoutPanel, BorderLayout.CENTER);
  }


  public abstract void refresh();

  private void addPackageToImportLayouts() {
    int selected = myImportLayoutTable.getSelectedRow() + 1;
    if (selected < 0) {
      selected = myImportLayoutList.getEntryCount();
    }
    PackageEntry entry = new PackageEntry(false, "", true);
    myImportLayoutList.insertEntryAt(entry, selected);
    refreshTableModel(selected, myImportLayoutTable);
  }

  private void addBlankLine() {
    int selected = myImportLayoutTable.getSelectedRow() + 1;
    if (selected < 0) {
      selected = myImportLayoutList.getEntryCount();
    }
    myImportLayoutList.insertEntryAt(PackageEntry.BLANK_LINE_ENTRY, selected);
    AbstractTableModel model = (AbstractTableModel)myImportLayoutTable.getModel();
    model.fireTableRowsInserted(selected, selected);
    myImportLayoutTable.setRowSelectionInterval(selected, selected);
  }

  private void removeEntryFromImportLayouts() {
    int selected = myImportLayoutTable.getSelectedRow();
    if (selected < 0) {
      return;
    }
    PackageEntry entry = myImportLayoutList.getEntryAt(selected);
    if (entry == PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY || entry == PackageEntry.ALL_OTHER_IMPORTS_ENTRY) {
      return;
    }
    TableUtil.stopEditing(myImportLayoutTable);
    myImportLayoutList.removeEntryAt(selected);
    AbstractTableModel model = (AbstractTableModel)myImportLayoutTable.getModel();
    model.fireTableRowsDeleted(selected, selected);
    if (selected >= myImportLayoutList.getEntryCount()) {
      selected--;
    }
    if (selected >= 0) {
      myImportLayoutTable.setRowSelectionInterval(selected, selected);
    }
  }

  private void moveRowUp() {
    int selected = myImportLayoutTable.getSelectedRow();
    if (selected < 1) {
      return;
    }
    TableUtil.stopEditing(myImportLayoutTable);
    PackageEntry entry = myImportLayoutList.getEntryAt(selected);
    PackageEntry previousEntry = myImportLayoutList.getEntryAt(selected - 1);
    myImportLayoutList.setEntryAt(previousEntry, selected);
    myImportLayoutList.setEntryAt(entry, selected - 1);

    AbstractTableModel model = (AbstractTableModel)myImportLayoutTable.getModel();
    model.fireTableRowsUpdated(selected - 1, selected);
    myImportLayoutTable.setRowSelectionInterval(selected - 1, selected - 1);
  }

  private void moveRowDown() {
    int selected = myImportLayoutTable.getSelectedRow();
    if (selected >= myImportLayoutList.getEntryCount() - 1) {
      return;
    }
    TableUtil.stopEditing(myImportLayoutTable);
    PackageEntry entry = myImportLayoutList.getEntryAt(selected);
    PackageEntry nextEntry = myImportLayoutList.getEntryAt(selected + 1);
    myImportLayoutList.setEntryAt(nextEntry, selected);
    myImportLayoutList.setEntryAt(entry, selected + 1);

    AbstractTableModel model = (AbstractTableModel)myImportLayoutTable.getModel();
    model.fireTableRowsUpdated(selected, selected + 1);
    myImportLayoutTable.setRowSelectionInterval(selected + 1, selected + 1);
  }

  public boolean areStaticImportsEnabled() {
    return myCbLayoutStaticImportsSeparately.isSelected();
  }

  public static JBTable createTableForPackageEntries(final PackageEntryTable packageTable, final ImportLayoutPanel panel) {
    final String[] names = {
      ApplicationBundle.message("listbox.import.package"),
      ApplicationBundle.message("listbox.import.with.subpackages"),
    };
    // Create a model of the data.
    TableModel dataModel = new AbstractTableModel() {
      public int getColumnCount() {
        return names.length + (panel.areStaticImportsEnabled() ? 1 : 0);
      }

      public int getRowCount() {
        return packageTable.getEntryCount();
      }

      @Nullable
      public Object getValueAt(int row, int col) {
        PackageEntry entry = packageTable.getEntryAt(row);
        if (entry == null || !isCellEditable(row, col)) return null;
        col += panel.areStaticImportsEnabled() ? 0 : 1;
        if (col == 0) {
          return entry.isStatic();
        }
        if (col == 1) {
          return entry.getPackageName();
        }
        if (col == 2) {
          return entry.isWithSubpackages();
        }
        throw new IllegalArgumentException(String.valueOf(col));
      }

      public String getColumnName(int column) {
        if (panel.areStaticImportsEnabled() && column == 0) return "Static";
        column -= panel.areStaticImportsEnabled() ? 1 : 0;
        return names[column];
      }

      public Class getColumnClass(int col) {
        col += panel.areStaticImportsEnabled() ? 0 : 1;
        if (col == 0) {
          return Boolean.class;
        }
        if (col == 1) {
          return String.class;
        }
        if (col == 2) {
          return Boolean.class;
        }
        throw new IllegalArgumentException(String.valueOf(col));
      }

      public boolean isCellEditable(int row, int col) {
        PackageEntry packageEntry = packageTable.getEntryAt(row);
        return !packageEntry.isSpecial();
      }

      public void setValueAt(Object aValue, int row, int col) {
        PackageEntry packageEntry = packageTable.getEntryAt(row);
        col += panel.areStaticImportsEnabled() ? 0 : 1;
        if (col == 0) {
          PackageEntry newPackageEntry = new PackageEntry((Boolean)aValue, packageEntry.getPackageName(), packageEntry.isWithSubpackages());
          packageTable.setEntryAt(newPackageEntry, row);
        }
        else if (col == 1) {
          PackageEntry newPackageEntry =
            new PackageEntry(packageEntry.isStatic(), ((String)aValue).trim(), packageEntry.isWithSubpackages());
          packageTable.setEntryAt(newPackageEntry, row);
        }
        else if (col == 2) {
          PackageEntry newPackageEntry =
            new PackageEntry(packageEntry.isStatic(), packageEntry.getPackageName(), ((Boolean)aValue).booleanValue());
          packageTable.setEntryAt(newPackageEntry, row);
        }
        else {
          throw new IllegalArgumentException(String.valueOf(col));
        }
      }
    };

    // Create the table
    final JBTable result = new JBTable(dataModel);
    result.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    resizeColumns(packageTable, result, panel.areStaticImportsEnabled());

    TableCellEditor editor = result.getDefaultEditor(String.class);
    if (editor instanceof DefaultCellEditor) {
      ((DefaultCellEditor)editor).setClickCountToStart(1);
    }

    TableCellEditor beditor = result.getDefaultEditor(Boolean.class);
    beditor.addCellEditorListener(new CellEditorListener() {
      public void editingStopped(ChangeEvent e) {
        if (panel.areStaticImportsEnabled()) {
          result.repaint(); // add/remove static keyword
        }
      }

      public void editingCanceled(ChangeEvent e) {
      }
    });

    return result;
  }

  public static void resizeColumns(final PackageEntryTable packageTable, JBTable result, boolean areStaticImportsEnabled) {
    ColoredTableCellRenderer packageRenderer = new ColoredTableCellRenderer() {
      @Override
      protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
        PackageEntry entry = packageTable.getEntryAt(row);

        if (entry == PackageEntry.BLANK_LINE_ENTRY) {
          append("<blank line>", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
        else {
          TextAttributes attributes = JavaHighlightingColors.KEYWORD.getDefaultAttributes();
          append("import", SimpleTextAttributes.fromTextAttributes(attributes));
          if (entry.isStatic()) {
            append(" ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
            append("static", SimpleTextAttributes.fromTextAttributes(attributes));
          }
          append(" ", SimpleTextAttributes.REGULAR_ATTRIBUTES);

          if (entry == PackageEntry.ALL_OTHER_IMPORTS_ENTRY || entry == PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY) {
            append("all other imports", SimpleTextAttributes.REGULAR_ATTRIBUTES);
          }
          else {
            append(entry.getPackageName() + ".*", SimpleTextAttributes.REGULAR_ATTRIBUTES);
          }
        }
      }
    };
    if (areStaticImportsEnabled) {
      fixColumnWidthToHeader(result, 0);
      fixColumnWidthToHeader(result, 2);
      result.getColumnModel().getColumn(1).setCellRenderer(packageRenderer);
      result.getColumnModel().getColumn(0).setCellRenderer(new BooleanTableCellRenderer());
      result.getColumnModel().getColumn(2).setCellRenderer(new BooleanTableCellRenderer());
    }
    else {
      fixColumnWidthToHeader(result, 1);
      result.getColumnModel().getColumn(0).setCellRenderer(packageRenderer);
      result.getColumnModel().getColumn(1).setCellRenderer(new BooleanTableCellRenderer());
    }
  }

  private static void fixColumnWidthToHeader(JBTable result, int columnIdx) {
    final TableColumn column = result.getColumnModel().getColumn(columnIdx);
    final int width =
      15 + result.getTableHeader().getFontMetrics(result.getTableHeader().getFont()).stringWidth(result.getColumnName(columnIdx));
    column.setMinWidth(width);
    column.setMaxWidth(width);
  }

  public static void refreshTableModel(int selectedRow, JBTable table) {
    AbstractTableModel model = (AbstractTableModel)table.getModel();
    model.fireTableRowsInserted(selectedRow, selectedRow);
    table.setRowSelectionInterval(selectedRow, selectedRow);
    TableUtil.editCellAt(table, selectedRow, 0);
    Component editorComp = table.getEditorComponent();
    if (editorComp != null) {
      editorComp.requestFocus();
    }
  }
}

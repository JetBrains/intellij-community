// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.highlighter.JavaHighlightingColors;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.codeStyle.PackageEntry;
import com.intellij.psi.codeStyle.PackageEntryTable;
import com.intellij.ui.*;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBInsets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import java.awt.*;

/**
 * @author Max Medvedev
 */
public abstract class ImportLayoutPanel extends JPanel {
  private final JBCheckBox myCbLayoutStaticImportsSeparately =
    new JBCheckBox(JavaBundle.message("import.layout.static.imports.separately"));
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
    setBorder(IdeBorderFactory.createTitledBorder(JavaBundle.message("title.import.layout"), false, JBInsets.emptyInsets()));

    myCbLayoutStaticImportsSeparately.addItemListener(e -> {
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
    });

    add(myCbLayoutStaticImportsSeparately, BorderLayout.NORTH);

    ActionGroup addGroup = new DefaultActionGroup(new AddPackageAction(), new AddBlankLineAction());
    addGroup.getTemplatePresentation().setIcon(LayeredIcon.ADD_WITH_DROPDOWN);
    addGroup.getTemplatePresentation().setText(JavaBundle.messagePointer("button.add"));
    addGroup.registerCustomShortcutSet(CommonShortcuts.getNewForDialogs(), null);

    JPanel importLayoutPanel = ToolbarDecorator.createDecorator(myImportLayoutTable = createTableForPackageEntries(myImportLayoutList, this))
      .addExtraAction(new AnActionButton.GroupPopupWrapper(addGroup))
      .setRemoveAction(button -> removeEntryFromImportLayouts())
      .setMoveUpAction(button -> moveRowUp())
      .setMoveDownAction(button -> moveRowDown())
      .setRemoveActionUpdater(e -> {
        int selectedImport = myImportLayoutTable.getSelectedRow();
        PackageEntry entry = selectedImport < 0 ? null : myImportLayoutList.getEntryAt(selectedImport);
        return entry != null && entry != PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY && entry != PackageEntry.ALL_OTHER_IMPORTS_ENTRY;
      })
      .setButtonComparator(JavaBundle.message("button.add"),
                           IdeBundle.message("action.remove"),
                           JavaBundle.message("import.layout.panel.up.button"),
                           JavaBundle.message("import.layout.panel.down.button"))
      .setPreferredSize(new Dimension(-1, 100)).createPanel();


    add(importLayoutPanel, BorderLayout.CENTER);
  }

  private class AddPackageAction extends DumbAwareAction {
    private AddPackageAction() {
      super(JavaBundle.messagePointer("button.add.package"), AllIcons.Nodes.Package);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      addPackageToImportLayouts();
    }
  }

  private class AddBlankLineAction extends DumbAwareAction {
    private AddBlankLineAction() {
      super(JavaBundle.messagePointer("button.add.blank"), AllIcons.Actions.SearchNewLine);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      addBlankLine();
    }
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
      JavaBundle.message("listbox.import.package"),
      JavaBundle.message("listbox.import.with.subpackages"),
    };
    // Create a model of the data.
    TableModel dataModel = new AbstractTableModel() {
      @Override
      public int getColumnCount() {
        return names.length + (panel.areStaticImportsEnabled() ? 1 : 0);
      }

      @Override
      public int getRowCount() {
        return packageTable.getEntryCount();
      }

      @Override
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

      @Override
      public String getColumnName(int column) {
        if (panel.areStaticImportsEnabled() && column == 0) return JavaBundle.message("listbox.import.static");
        column -= panel.areStaticImportsEnabled() ? 1 : 0;
        return names[column];
      }

      @Override
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

      @Override
      public boolean isCellEditable(int row, int col) {
        PackageEntry packageEntry = packageTable.getEntryAt(row);
        return !packageEntry.isSpecial();
      }

      @Override
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
    result.setShowGrid(false);
    result.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    resizeColumns(packageTable, result, panel.areStaticImportsEnabled());

    TableCellEditor editor = result.getDefaultEditor(String.class);
    if (editor instanceof DefaultCellEditor) {
      ((DefaultCellEditor)editor).setClickCountToStart(1);
    }

    TableCellEditor beditor = result.getDefaultEditor(Boolean.class);
    beditor.addCellEditorListener(new CellEditorListener() {
      @Override
      public void editingStopped(ChangeEvent e) {
        if (panel.areStaticImportsEnabled()) {
          result.repaint(); // add/remove static keyword
        }
      }

      @Override
      public void editingCanceled(ChangeEvent e) {
      }
    });

    return result;
  }

  public static void resizeColumns(final PackageEntryTable packageTable, JBTable result, boolean areStaticImportsEnabled) {
    ColoredTableCellRenderer packageRenderer = new ColoredTableCellRenderer() {
      @Override
      protected void customizeCellRenderer(@NotNull JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
        PackageEntry entry = packageTable.getEntryAt(row);

        if (entry == PackageEntry.BLANK_LINE_ENTRY) {
          append(JavaBundle.message("import.layout.panel.blank.line.entry"), SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
        else {
          TextAttributes attributes = JavaHighlightingColors.KEYWORD.getDefaultAttributes();
          append(PsiKeyword.IMPORT, SimpleTextAttributes.fromTextAttributes(attributes));
          if (entry.isStatic()) {
            append(" ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
            append(PsiKeyword.STATIC, SimpleTextAttributes.fromTextAttributes(attributes));
          }
          append(" ", SimpleTextAttributes.REGULAR_ATTRIBUTES);

          if (entry == PackageEntry.ALL_OTHER_IMPORTS_ENTRY || entry == PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY) {
            append(JavaBundle.message("import.layout.panel.all.other.imports"), SimpleTextAttributes.REGULAR_ATTRIBUTES);
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
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(editorComp, true));
    }
  }
}

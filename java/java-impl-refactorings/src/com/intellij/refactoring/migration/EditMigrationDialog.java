// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.migration;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;

public class EditMigrationDialog extends DialogWrapper {
  private EditMigrationDialogUi myUi;
  private JBTable myTable;
  private final Project myProject;
  private final MigrationMap myMigrationMap;
  private final MigrationMapSet myMigrationMapSet;
  private final String myEditedMapName;

  /**
   * This dialog makes it possible to choose a name, description, and edit a table of {@link MigrationMapEntry}.
   *
   * @param migrationMap The map to edit. The name and description are
   * @param editedMapName Name of the existing map to be overridden. Blank if editing a new map.
   */
  public EditMigrationDialog(Project project, MigrationMap migrationMap, MigrationMapSet migrationMapSet, String editedMapName) {
    super(project, true);
    myProject = project;
    myMigrationMap = migrationMap;
    myMigrationMapSet = migrationMapSet;
    myEditedMapName = editedMapName;
    setHorizontalStretch(1.2f);
    if (editedMapName.isEmpty()) {
      setTitle(JavaRefactoringBundle.message("edit.migration.map.title.new"));
    } else {
      setTitle(JavaRefactoringBundle.message("edit.migration.map.title.existing"));
    }
    setOKButtonText(JavaRefactoringBundle.message("edit.migration.map.ok.button"));
    init();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myUi.getNameField();
  }

  public @Nls String getName() {
    @NlsSafe String text = myUi.getNameField().getText();
    return text;
  }

  public @Nls String getDescription() {
    @NlsSafe String text = myUi.getDescriptionTextArea().getText();
    return text;
  }

  @Override
  protected JComponent createCenterPanel() {
    myUi = new EditMigrationDialogUi(myMigrationMapSet,
                                     ToolbarDecorator.createDecorator(createTable())
                                       .setAddAction(button -> addRow())
                                       .setRemoveAction(button -> removeSelectedRow())
                                       .setEditAction(button -> edit())
                                       .setMoveUpAction(button -> moveUp())
                                       .setMoveDownAction(button -> moveDown())
                                       .createPanel(),
                                     this);

    final JTextField nameField = myUi.getNameField();
    nameField.setText(myMigrationMap.getName());
    myUi.getDescriptionTextArea().setText(myMigrationMap.getDescription());

    return myUi.getPanel();
  }

  private void edit() {
    EditMigrationEntryDialog dialog = new EditMigrationEntryDialog(myProject);
    int selected = myTable.getSelectedRow();
    if (selected < 0) {
      return;
    }
    MigrationMapEntry entry = myMigrationMap.getEntryAt(selected);
    dialog.setEntry(entry);
    if (!dialog.showAndGet()) {
      return;
    }
    dialog.updateEntry(entry);
    AbstractTableModel model = (AbstractTableModel)myTable.getModel();
    model.fireTableRowsUpdated(selected, selected);
  }

  private void addRow() {
    EditMigrationEntryDialog dialog = new EditMigrationEntryDialog(myProject);
    MigrationMapEntry entry = new MigrationMapEntry();
    dialog.setEntry(entry);
    if (!dialog.showAndGet()) {
      return;
    }
    dialog.updateEntry(entry);
    addEntry(entry);
  }

  private void addEntry(MigrationMapEntry entry) {
    myMigrationMap.addEntry(entry);
    AbstractTableModel model = (AbstractTableModel)myTable.getModel();
    model.fireTableRowsInserted(myMigrationMap.getEntryCount() - 1, myMigrationMap.getEntryCount() - 1);
    myTable.setRowSelectionInterval(myMigrationMap.getEntryCount() - 1, myMigrationMap.getEntryCount() - 1);
  }

  private void removeSelectedRow() {
    int selected = myTable.getSelectedRow();
    if (selected < 0) {
      return;
    }
    removeRow(selected);
  }

  private void removeRow(int selected) {
    myMigrationMap.removeEntryAt(selected);
    AbstractTableModel model = (AbstractTableModel)myTable.getModel();
    model.fireTableRowsDeleted(selected, selected);
    if (selected >= myMigrationMap.getEntryCount()) {
      selected--;
    }
    if (selected >= 0) {
      myTable.setRowSelectionInterval(selected, selected);
    }
  }

  private void moveUp() {
    int selected = myTable.getSelectedRow();
    if (selected < 1) {
      return;
    }
    MigrationMapEntry entry = myMigrationMap.getEntryAt(selected);
    MigrationMapEntry previousEntry = myMigrationMap.getEntryAt(selected - 1);
    myMigrationMap.setEntryAt(previousEntry, selected);
    myMigrationMap.setEntryAt(entry, selected - 1);
    AbstractTableModel model = (AbstractTableModel)myTable.getModel();
    model.fireTableRowsUpdated(selected - 1, selected);
    myTable.setRowSelectionInterval(selected - 1, selected - 1);
  }

  private void moveDown() {
    int selected = myTable.getSelectedRow();
    if (selected >= myMigrationMap.getEntryCount() - 1) {
      return;
    }
    MigrationMapEntry entry = myMigrationMap.getEntryAt(selected);
    MigrationMapEntry nextEntry = myMigrationMap.getEntryAt(selected + 1);
    myMigrationMap.setEntryAt(nextEntry, selected);
    myMigrationMap.setEntryAt(entry, selected + 1);
    AbstractTableModel model = (AbstractTableModel)myTable.getModel();
    model.fireTableRowsUpdated(selected, selected + 1);
    myTable.setRowSelectionInterval(selected + 1, selected + 1);
  }

  private JBTable createTable() {
    final String[] names = {
      JavaRefactoringBundle.message("migration.type.column.header"),
      JavaRefactoringBundle.message("migration.old.name.column.header"),
      JavaRefactoringBundle.message("migration.new.name.column.header")};

    // Create a model of the data.
    TableModel dataModel = new AbstractTableModel() {
      @Override
      public int getColumnCount() {
        return 3;
      }

      @Override
      public int getRowCount() {
        return myMigrationMap.getEntryCount();
      }

      @Override
      public Object getValueAt(int row, int col) {
        MigrationMapEntry entry = myMigrationMap.getEntryAt(row);
        if (col == 0) {
          if (entry.getType() == MigrationMapEntry.PACKAGE && entry.isRecursive()) {
            return JavaRefactoringBundle.message("migration.package.with.subpackages");
          }
          else if (entry.getType() == MigrationMapEntry.PACKAGE && !entry.isRecursive()) {
            return JavaRefactoringBundle.message("migration.package");
          }
          else {
            return JavaRefactoringBundle.message("migration.class");
          }
        }

        String suffix = (entry.getType() == MigrationMapEntry.PACKAGE ? ".*" : "");
        if (col == 1) {
          return entry.getOldName() + suffix;
        }
        else {
          return entry.getNewName() + suffix;
        }
      }

      @Override
      public String getColumnName(int column) {
        return names[column];
      }

      @Override
      public Class<String> getColumnClass(int c) {
        return String.class;
      }

      @Override
      public boolean isCellEditable(int row, int col) {
        return false;
      }

      @Override
      public void setValueAt(Object aValue, int row, int column) {
      }
    };

    // Create the table
    myTable = new JBTable(dataModel);
    myTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    myTable.setVisibleRowCount(10);

    return myTable;
  }

  public void copyMap(@Nullable String mapName) {
    if (mapName == null) return;
    final MigrationMap copiedMap = myMigrationMapSet.findMigrationMap(mapName);
    if (copiedMap == null) return;

    myUi.getNameField().setText(copiedMap.getName());
    myUi.getDescriptionTextArea().setText(copiedMap.getDescription());
    while (myTable.getRowCount() > 0) removeRow(0);
    for (int i = 0; i < copiedMap.getEntryCount(); i++) {
      addEntry(copiedMap.getEntryAt(i));
    }
  }

  public ValidationInfo validateName(@Nullable String text) {
    if (text.isBlank()) {
      return new ValidationInfo(JavaRefactoringBundle.message("migration.edit.empty.name"));
    }

    if (myMigrationMapSet.findMigrationMap(text) != null && (myEditedMapName.isEmpty() || !text.equals(myEditedMapName))) {
      // new map with existing name || edited map with another existing name
      return new ValidationInfo(JavaRefactoringBundle.message("migration.edit.existing.name"));
    }
    return null;
  }

  public ValidationInfo validateTable() {
    if (myMigrationMap.getEntryCount() == 0) {
      return new ValidationInfo(JavaRefactoringBundle.message("migration.edit.empty.table"));
    }
    return null;
  }
}
// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.migration;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;
import java.awt.*;

public class EditMigrationDialog extends DialogWrapper{
  private JBTable myTable;
  private JTextField myNameField;
  private JTextArea myDescriptionTextArea;
  private final Project myProject;
  private final MigrationMap myMigrationMap;

  public EditMigrationDialog(Project project, MigrationMap migrationMap) {
    super(project, true);
    myProject = project;
    myMigrationMap = migrationMap;
    setHorizontalStretch(1.2f);
    setTitle(JavaRefactoringBundle.message("edit.migration.map.title"));
    init();
    validateOKButton();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  private void validateOKButton() {
    boolean isEnabled = true;
    if (myNameField.getText().trim().length() == 0) {
      isEnabled = false;
    } else if (myMigrationMap.getEntryCount() == 0) {
      isEnabled = false;
    }
    setOKActionEnabled(isEnabled);
  }

  public @Nls String getName() {
    @NlsSafe String text = myNameField.getText();
    return text;
  }

  public @Nls String getDescription() {
    @NlsSafe String text = myDescriptionTextArea.getText();
    return text;
  }

  @Override
  protected JComponent createNorthPanel() {
    myNameField = new JTextField(myMigrationMap.getName());
    myNameField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        validateOKButton();
      }
    });

    myDescriptionTextArea = new JTextArea(myMigrationMap.getDescription(), 3, 40) {
      @Override
      public Dimension getMinimumSize() {
        return super.getPreferredSize();
      }
    };
    myDescriptionTextArea.setLineWrap(true);
    myDescriptionTextArea.setWrapStyleWord(true);
    myDescriptionTextArea.setFont(myNameField.getFont());
    myDescriptionTextArea.setBackground(myNameField.getBackground());
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myDescriptionTextArea);
    scrollPane.setBorder(myNameField.getBorder());

    return FormBuilder.createFormBuilder()
      .addLabeledComponent(new JLabel(JavaRefactoringBundle.message("migration.map.name.prompt")), myNameField)
      .addLabeledComponent(new JLabel(JavaRefactoringBundle.message("migration.map.description.label")), scrollPane)
      .addVerticalGap(UIUtil.LARGE_VGAP).getPanel();
  }

  @Override
  protected JComponent createCenterPanel() {
    return ToolbarDecorator.createDecorator(createTable())
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          addRow();
          validateOKButton();
        }
      }).setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          removeRow();
          validateOKButton();
        }
      }).setEditAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          edit();
        }
      }).setMoveUpAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          moveUp();
        }
      }).setMoveDownAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          moveDown();
        }
      }).createPanel();
  }

  private void edit() {
    EditMigrationEntryDialog dialog = new EditMigrationEntryDialog(myProject);
    int selected = myTable.getSelectedRow();
    if (selected < 0)
      return;
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
    myMigrationMap.addEntry(entry);
    AbstractTableModel model = (AbstractTableModel)myTable.getModel();
    model.fireTableRowsInserted(myMigrationMap.getEntryCount() - 1, myMigrationMap.getEntryCount() - 1);
    myTable.setRowSelectionInterval(myMigrationMap.getEntryCount() - 1, myMigrationMap.getEntryCount() - 1);
  }

  private void removeRow() {
    int selected = myTable.getSelectedRow();
    if (selected < 0)
      return;
    myMigrationMap.removeEntryAt(selected);
    AbstractTableModel model = (AbstractTableModel)myTable.getModel();
    model.fireTableRowsDeleted(selected, selected);
    if (selected >= myMigrationMap.getEntryCount()){
      selected--;
    }
    if (selected >= 0){
      myTable.setRowSelectionInterval(selected, selected);
    }
  }

  private void moveUp() {
    int selected = myTable.getSelectedRow();
    if (selected < 1)
      return;
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
    if (selected >= myMigrationMap.getEntryCount() - 1)
      return;
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
        if (col == 0){
          if (entry.getType() == MigrationMapEntry.PACKAGE && entry.isRecursive()){
            return JavaRefactoringBundle.message("migration.package.with.subpackages");
          }
          else if (entry.getType() == MigrationMapEntry.PACKAGE && !entry.isRecursive()){
            return JavaRefactoringBundle.message("migration.package");
          }
          else{
            return JavaRefactoringBundle.message("migration.class");
          }
        }

        String suffix = (entry.getType() == MigrationMapEntry.PACKAGE ? ".*" : "");
        if (col == 1){
          return entry.getOldName() + suffix;
        }
        else{
          return entry.getNewName() + suffix;
        }
      }

      @Override
      public String getColumnName(int column) {
        return names[column];
      }

      @Override
      public Class getColumnClass(int c) {
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
}
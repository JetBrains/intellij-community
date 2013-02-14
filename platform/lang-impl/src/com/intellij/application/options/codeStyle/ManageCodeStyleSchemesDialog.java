/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.application.options.codeStyle;

import com.intellij.application.options.ExportSchemeAction;
import com.intellij.application.options.SaveSchemeDialog;
import com.intellij.application.options.SchemesToImportPopup;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.options.SchemesManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSchemes;
import com.intellij.psi.impl.source.codeStyle.CodeStyleSchemeImpl;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

/**
 * @author: rvishnyakov
 */
public class ManageCodeStyleSchemesDialog extends DialogWrapper {

  private JPanel myContentPane;
  private JBTable mySchemesTable;
  private JButton myDeleteButton;
  private JButton mySaveAsButton;
  private JButton myCopyToProjectButton;
  private JButton myCloseButton;
  private JButton myExportButton;
  private JButton myImportButton;
  private final MySchemesTableModel mySchemesTableModel;
  private final CodeStyleSchemesModel myModel;
  private final Component myParent;


  protected ManageCodeStyleSchemesDialog(final Component parent, CodeStyleSchemesModel schemesModel) {
    super(parent, true);
    myParent = parent;
    myModel = schemesModel;
    setTitle("Code Style Schemes");
    mySchemesTableModel = new MySchemesTableModel(schemesModel);
    mySchemesTable.setModel(mySchemesTableModel);
    mySchemesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    mySchemesTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        updateActions();
      }
    });
    setDefaultSelection();


    myDeleteButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        deleteSelected();
      }
    });
    mySaveAsButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        onSaveAs();
      }
    });
    myCopyToProjectButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        onCopyToProject();
      }
    });
    myCloseButton.addActionListener(new ActionListener(){
      @Override
      public void actionPerformed(ActionEvent e) {
        doCancelAction();
      }
    });

    final SchemesManager<CodeStyleScheme, CodeStyleSchemeImpl> schemesManager = CodeStyleSchemesModel.getSchemesManager();

    if (schemesManager.isExportAvailable()) {
      myExportButton.setVisible(true);
      myExportButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(final ActionEvent e) {
          CodeStyleScheme selected = getSelectedScheme();
          ExportSchemeAction.doExport((CodeStyleSchemeImpl)selected, schemesManager);
        }
      });
      myExportButton.setMnemonic('S');
    }
    else {
      myExportButton.setVisible(false);
    }

    if (schemesManager.isImportAvailable()) {
      myImportButton.setVisible(true);
      myImportButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(final ActionEvent e) {
          SchemesToImportPopup<CodeStyleScheme, CodeStyleSchemeImpl> popup =
            new SchemesToImportPopup<CodeStyleScheme, CodeStyleSchemeImpl>(parent) {
              @Override
              protected void onSchemeSelected(final CodeStyleSchemeImpl scheme) {
                if (scheme != null) {
                  myModel.addScheme(scheme, true);
                }
              }
            };
          popup.show(schemesManager, myModel.getSchemes());
        }
      });
    }
    else {
      myImportButton.setVisible(false);
    }

    init();
  }

  private void updateActions() {
    CodeStyleScheme selectedScheme = getSelectedScheme();
    myDeleteButton.setEnabled(!(selectedScheme.isDefault() || mySchemesTableModel.isProjectScheme(selectedScheme)));
    myCopyToProjectButton.setEnabled(!mySchemesTableModel.isProjectScheme(selectedScheme));
  }

  @NotNull
  private CodeStyleScheme getSelectedScheme() {
    int row = mySchemesTable.getSelectedRow();
    assert row >= 0;
    return mySchemesTableModel.getSchemeAt(row);
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    return new Action[]{};
  }

  private void deleteSelected() {
    int row = mySchemesTable.getSelectedRow();
    if (row >= 0) {
      int rowToSelect = row + 1;
      if (rowToSelect >= mySchemesTableModel.getRowCount()) {
        rowToSelect = mySchemesTableModel.getDefaultRow();
      }
      mySchemesTable.getSelectionModel().setSelectionInterval(rowToSelect, rowToSelect);
      mySchemesTableModel.deleteAt(row);
    }
  }

  @Override
  protected JComponent createCenterPanel() {
    return myContentPane;
  }

  private void createUIComponents() {
    mySchemesTable = new MySchemesTable();
  }

  private class MySchemesTable extends JBTable {
    private final TableCellRenderer myFixedItemsRenderer;

    private MySchemesTable() {
      myFixedItemsRenderer = new DefaultTableCellRenderer() {
        @Override
        public Component getTableCellRendererComponent(JTable table,
                                                       Object value,
                                                       boolean isSelected,
                                                       boolean hasFocus,
                                                       int row,
                                                       int column) {
          Component defaultComponent = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
          if (value instanceof CodeStyleScheme) {
            CodeStyleScheme scheme = (CodeStyleScheme)value;
            if (scheme.isDefault() || myModel.isProjectScheme(scheme)) {
              defaultComponent.setFont(defaultComponent.getFont().deriveFont(Font.BOLD));
            }
          }
          return defaultComponent;
        }
      };
    }

    @Override
    public TableCellRenderer getCellRenderer(int row, int column) {
      return myFixedItemsRenderer;
    }
  }

  private static class MySchemesTableModel extends AbstractTableModel {
    private final CodeStyleSchemesModel mySchemesModel;
    private final List<CodeStyleScheme> mySchemes;

    public MySchemesTableModel(CodeStyleSchemesModel schemesModel) {
      mySchemesModel = schemesModel;
      mySchemes = new ArrayList<CodeStyleScheme>();
      updateSchemes();
    }

    @Override
    public String getColumnName(int column) {
      assert column == 0;
      return "Name";
    }

    @Override
    public int getRowCount() {
      return mySchemes.size();
    }

    @Override
    public int getColumnCount() {
      return 1;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      assert columnIndex == 0;
      return mySchemes.get(rowIndex);
    }

    public CodeStyleScheme getSchemeAt(int row) {
      return mySchemes.get(row);
    }

    public void deleteAt(int row) {
      CodeStyleScheme scheme = mySchemes.get(row);
      mySchemesModel.removeScheme(scheme);
      updateSchemes();
      fireTableRowsDeleted(row, row);
    }

    public int createNewScheme(CodeStyleScheme selectedScheme, String schemeName) {
      CodeStyleScheme newScheme = mySchemesModel.createNewScheme(schemeName, selectedScheme);
      mySchemesModel.addScheme(newScheme, true);
      updateSchemes();
      int row = 0;
      for (CodeStyleScheme scheme : mySchemes) {
        if (scheme == newScheme) {
          fireTableRowsInserted(row, row);
          break;
        }
        row ++;
      }
      return row;
    }
    
    public int getDefaultRow() {
      int row = 0;
      for (CodeStyleScheme scheme : mySchemes) {
        if (scheme.isDefault()) return row;
        row ++;
      }
      return 0;
    }

    public void copyToProject(CodeStyleScheme scheme) {
      mySchemesModel.copyToProject(scheme);
      int switchToProject = Messages
        .showYesNoDialog("Scheme '" + scheme.getName() + "' was copied to be used as the project scheme.\n" +
                         "Switch to this created scheme?",
                         "Copy Scheme to Project", Messages.getQuestionIcon());
      if (switchToProject == 0) {
        mySchemesModel.setUsePerProjectSettings(true, true);
      }
    }

    public int exportProjectScheme() {
      String name = Messages.showInputDialog("Enter new scheme name:", "Copy Project Scheme to Global List", Messages.getQuestionIcon());
      if (name != null && !CodeStyleSchemesModel.PROJECT_SCHEME_NAME.equals(name)) {
        CodeStyleScheme newScheme = mySchemesModel.exportProjectScheme(name);
        updateSchemes();
        int switchToGlobal = Messages
          .showYesNoDialog("Project scheme was copied to global scheme list as '" + newScheme.getName() + "'.\n" +
                           "Switch to this created scheme?",
                           "Copy Project Scheme to Global List", Messages.getQuestionIcon());
        if (switchToGlobal == 0) {
          mySchemesModel.setUsePerProjectSettings(false);
          mySchemesModel.selectScheme(newScheme, null);
        }
        int row = 0;
        for (CodeStyleScheme scheme : mySchemes) {
          if (scheme == newScheme) {
            fireTableRowsInserted(row, row);
            return switchToGlobal == 0 ? row : -1;
          }
          row++;
        }
      }
      return -1;
    }

    private void updateSchemes() {
      mySchemes.clear();
      mySchemes.addAll(mySchemesModel.getAllSortedSchemes());
    }


    public boolean isProjectScheme(CodeStyleScheme scheme) {
      return mySchemesModel.isProjectScheme(scheme);
    }

  }

  private void onSaveAs() {
    if (mySchemesTableModel.isProjectScheme(getSelectedScheme())) {
      int rowToSelect = mySchemesTableModel.exportProjectScheme();
      if (rowToSelect > 0) {
        mySchemesTable.getSelectionModel().setSelectionInterval(rowToSelect, rowToSelect);
      }
    }
    else {
      CodeStyleScheme[] schemes = CodeStyleSchemes.getInstance().getSchemes();
      ArrayList<String> names = new ArrayList<String>();
      for (CodeStyleScheme scheme : schemes) {
        names.add(scheme.getName());
      }
      SaveSchemeDialog saveDialog = new SaveSchemeDialog(myParent, ApplicationBundle.message("title.save.code.style.scheme.as"), names);
      saveDialog.show();
      if (saveDialog.isOK()) {
        int row = mySchemesTableModel.createNewScheme(getSelectedScheme(), saveDialog.getSchemeName());
        mySchemesTable.getSelectionModel().setSelectionInterval(row, row);
      }
    }
  }

  private void onCopyToProject() {
    mySchemesTableModel.copyToProject(getSelectedScheme());
  }

  private void setDefaultSelection() {
    CodeStyleScheme selectedScheme = myModel.getSelectedScheme();
    for (int i = 0; i < mySchemesTableModel.getRowCount(); i ++) {
      if (mySchemesTableModel.getSchemeAt(i).equals(selectedScheme)) {
        mySchemesTable.getSelectionModel().setSelectionInterval(i, i);
        return;
      }
    }
  }

}

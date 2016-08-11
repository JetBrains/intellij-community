/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.application.options.ImportSourceChooserDialog;
import com.intellij.application.options.SaveSchemeDialog;
import com.intellij.application.options.SchemesToImportPopup;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.options.*;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.impl.source.codeStyle.CodeStyleSchemesImpl;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
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
    mySchemesTable.getSelectionModel().addListSelectionListener(e -> updateActions());
    setDefaultSelection();


    myDeleteButton.addActionListener(e -> deleteSelected());
    mySaveAsButton.addActionListener(e -> onSaveAs());
    myCopyToProjectButton.addActionListener(e -> onCopyToProject());
    myCloseButton.addActionListener(e -> doCancelAction());

    myExportButton.setVisible(false);

    if (SchemeImporterEP.getExtensions(CodeStyleScheme.class).isEmpty()) {
      myImportButton.setVisible(false);
    }
    else {
      myImportButton.setVisible(true);
      myImportButton.addActionListener(e -> chooseAndImport());
    }

    if (SchemeExporterEP.getExtensions(CodeStyleScheme.class).isEmpty()) {
      myExportButton.setVisible(false);
    }
    else {
      myExportButton.setVisible(true);
      myExportButton.addActionListener(e -> exportSelectedScheme());
    }

    init();
  }

  private void chooseAndImport() {
    ImportSourceChooserDialog<CodeStyleScheme> importSourceChooserDialog =
      new ImportSourceChooserDialog<>(myContentPane, CodeStyleScheme.class);
    if (importSourceChooserDialog.showAndGet()) {
      if (importSourceChooserDialog.isImportFromSharedSelected()) {
        new SchemesToImportPopup<CodeStyleScheme>(myContentPane) {
          @Override
          protected void onSchemeSelected(CodeStyleScheme scheme) {
            if (scheme != null) {
              myModel.addScheme(scheme, true);
            }
          }
        }.show(myModel.getSchemes());
      }
      else {
        final String selectedImporterName = importSourceChooserDialog.getSelectedSourceName();
        if (selectedImporterName != null) {
          final SchemeImporter<CodeStyleScheme> importer = SchemeImporterEP.getImporter(selectedImporterName, CodeStyleScheme.class);
          if (importer == null) return;
          try {
            final CodeStyleScheme scheme = importExternalCodeStyle(importer);
            if (scheme != null) {
              final String additionalImportInfo = StringUtil.notNullize(importer.getAdditionalImportInfo(scheme));
              SchemeImportUtil
                .showStatus(myImportButton,
                         ApplicationBundle.message("message.code.style.scheme.import.success", selectedImporterName, scheme.getName(), additionalImportInfo),
                         MessageType.INFO);
            }
          }
          catch (SchemeImportException e) {
            if (e.isWarning()) {
              SchemeImportUtil.showStatus(myImportButton, e.getMessage(), MessageType.WARNING);
              return;
            }
            final String message = ApplicationBundle.message("message.code.style.scheme.import.failure", selectedImporterName, e.getMessage());
            SchemeImportUtil.showStatus(myImportButton, message, MessageType.ERROR);
          }
        }
      }
    }
  }

  @Nullable
  private CodeStyleScheme importExternalCodeStyle(final SchemeImporter<CodeStyleScheme> importer) throws SchemeImportException {
    final VirtualFile selectedFile = SchemeImportUtil
      .selectImportSource(importer.getSourceExtensions(), myContentPane, CodeStyleSchemesUIConfiguration.Util.getRecentImportFile());
    if (selectedFile != null) {
      CodeStyleSchemesUIConfiguration.Util.setRecentImportFile(selectedFile);
      final SchemeCreator schemeCreator = new SchemeCreator();
      final CodeStyleScheme
        schemeImported = importer.importScheme(myModel.getProject(), selectedFile, getSelectedScheme(), schemeCreator);
      if (schemeImported != null) {
        if (schemeCreator.isSchemeWasCreated()) myModel.fireSchemeListChanged();
        else myModel.fireSchemeChanged(schemeImported);
        return schemeImported;
      }
    }
    return null;
  }

  private void updateActions() {
    // there is a possibility that nothing will be selected in a table. So we just need to corresponding disable actions
    final CodeStyleScheme selectedScheme = getSelectedInTableScheme();
    myDeleteButton.setEnabled(selectedScheme != null && (!(selectedScheme.isDefault() || mySchemesTableModel.isProjectScheme(selectedScheme))));
    myCopyToProjectButton.setEnabled(selectedScheme != null && !mySchemesTableModel.isProjectScheme(selectedScheme));
  }

  @Nullable
  private CodeStyleScheme getSelectedInTableScheme() {
    int row = mySchemesTable.getSelectedRow();
    if (row < 0) return null;
    return mySchemesTableModel.getSchemeAt(row);
  }

  @NotNull
  private CodeStyleScheme getSelectedScheme() {
    int row = mySchemesTable.getSelectedRow();
    if (row < 0) row = mySchemesTableModel.getDefaultRow();
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

  private class SchemeCreator implements SchemeFactory<CodeStyleScheme> {
    private boolean mySchemeWasCreated;

    @Override
    public CodeStyleScheme createNewScheme(@Nullable String targetName) {
      mySchemeWasCreated = true;
      if (targetName == null) targetName = ApplicationBundle.message("code.style.scheme.import.unnamed");
      final int row = mySchemesTableModel.createNewScheme(getSelectedScheme(), targetName);

      mySchemesTable.getSelectionModel().setSelectionInterval(row, row);
      return mySchemesTableModel.getSchemeAt(row);
    }

    public boolean isSchemeWasCreated() {
      return mySchemeWasCreated;
    }
  }

  private class MySchemesTable extends JBTable {
    private final TableCellRenderer myFixedItemsRenderer;

    private MySchemesTable() {
      myFixedItemsRenderer = new DefaultTableCellRenderer() {
        @NotNull
        @Override
        public Component getTableCellRendererComponent(@NotNull JTable table,
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
      mySchemes = new ArrayList<>();
      updateSchemes();
    }

    @NotNull
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
      if (switchToProject == Messages.YES) {
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
        if (switchToGlobal == Messages.YES) {
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
      Collection<String> names = CodeStyleSchemesImpl.getSchemeManager().getAllSchemeNames();
      String selectedName = getSelectedScheme().getName();
      SaveSchemeDialog saveDialog =
        new SaveSchemeDialog(myParent, ApplicationBundle.message("title.save.code.style.scheme.as"), names, selectedName);
      if (saveDialog.showAndGet()) {
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

  private void exportSelectedScheme() {
    new CodeStyleSchemeExporterUI(myExportButton, getSelectedScheme(),
                                  (message, messageType) -> SchemeImportUtil.showStatus(myExportButton, message, messageType)).export();
  }
}

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
package com.intellij.application.options;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.PackageEntry;
import com.intellij.psi.codeStyle.PackageEntryTable;
import com.intellij.ui.OptionGroup;
import com.intellij.ui.TableUtil;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;

public class CodeStyleImportsPanel extends JPanel {
  private JCheckBox myCbUseFQClassNames;
  private JCheckBox myCbUseSingleClassImports;
  private JCheckBox myCbInsertInnerClassImports;
  private JTextField myClassCountField;
  private JTextField myNamesCountField;
  private final PackageEntryTable myPackageList = new PackageEntryTable();

  private JBTable myPackageTable;
  private final CodeStyleSettings mySettings;

  private JPanel myGeneralPanel;
  private JPanel myPackagesPanel;
  private JPanel myImportsLayoutPanel;
  private JPanel myWholePanel;
  private ImportLayoutPanel myImportLayoutPanel;
  private FullyQualifiedNamesInJavadocOptionProvider myFqnInJavadocOption;

  public CodeStyleImportsPanel(CodeStyleSettings settings) {
    mySettings = settings;
    setLayout(new BorderLayout());
    add(myWholePanel, BorderLayout.CENTER);

    myGeneralPanel.add(createGeneralOptionsPanel(), BorderLayout.CENTER);
    createImportPanel();
    createPackagePanel();
  }

  private void createImportPanel() {
    myImportLayoutPanel = new ImportLayoutPanel() {
      @Override
      public void refresh() {
        refreshTable(myPackageTable, myPackageList);
        refreshTable(getImportLayoutTable(), getImportLayoutList());
      }
    };
    myImportsLayoutPanel.add(myImportLayoutPanel, BorderLayout.CENTER);
  }

  private void createPackagePanel() {
    myPackageTable = ImportLayoutPanel.createTableForPackageEntries(myPackageList, myImportLayoutPanel);
    myPackagesPanel.add(PackagePanel.createPackagesPanel(myPackageTable, myPackageList), BorderLayout.CENTER);
  }

  private JPanel createGeneralOptionsPanel() {
    OptionGroup group = new OptionGroup(ApplicationBundle.message("title.general"));
    myCbUseSingleClassImports = new JCheckBox(ApplicationBundle.message("checkbox.use.single.class.import"));
    group.add(myCbUseSingleClassImports);

    myCbUseFQClassNames = new JCheckBox(ApplicationBundle.message("checkbox.use.fully.qualified.class.names"));
    group.add(myCbUseFQClassNames);

    myCbInsertInnerClassImports = new JCheckBox(ApplicationBundle.message("checkbox.insert.imports.for.inner.classes"));
    group.add(myCbInsertInnerClassImports);

    myFqnInJavadocOption = new FullyQualifiedNamesInJavadocOptionProvider(mySettings);
    group.add(myFqnInJavadocOption.getPanel());
    
    myClassCountField = new JTextField(3);
    myNamesCountField = new JTextField(3);
    final JPanel panel = new JPanel(new GridBagLayout());
    panel.add(new JLabel(ApplicationBundle.message("editbox.class.count.to.use.import.with.star")),
              new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE,
                                     JBUI.insetsLeft(3), 0, 0));
    panel.add(myClassCountField,
              new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE,
                                     JBUI.insetsLeft(1), 0, 0));
    panel.add(new JLabel(ApplicationBundle.message("editbox.names.count.to.use.static.import.with.star")),
              new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 1.0, GridBagConstraints.WEST, GridBagConstraints.NONE,
                                     JBUI.insetsLeft(3), 0, 0));
    panel.add(myNamesCountField,
              new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.WEST, GridBagConstraints.NONE,
                                     JBUI.insetsLeft(1), 0, 0));

    group.add(panel);
    return group.createPanel();
  }

  private void refreshTable(final JBTable table, final PackageEntryTable packageTable) {
    AbstractTableModel model = (AbstractTableModel)table.getModel();
    table.createDefaultColumnsFromModel();
    model.fireTableDataChanged();
    ImportLayoutPanel.resizeColumns(packageTable, table, myImportLayoutPanel.areStaticImportsEnabled());
  }

  public void reset(CodeStyleSettings settings) {
    myCbUseFQClassNames.setSelected(settings.USE_FQ_CLASS_NAMES);
    myCbUseSingleClassImports.setSelected(settings.USE_SINGLE_CLASS_IMPORTS);
    myCbInsertInnerClassImports.setSelected(settings.INSERT_INNER_CLASS_IMPORTS);
    myClassCountField.setText(Integer.toString(settings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND));
    myNamesCountField.setText(Integer.toString(settings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND));

    myImportLayoutPanel.getImportLayoutList().copyFrom(settings.IMPORT_LAYOUT_TABLE);
    myPackageList.copyFrom(settings.PACKAGES_TO_USE_IMPORT_ON_DEMAND);
    myFqnInJavadocOption.reset(settings);

    myImportLayoutPanel.getCbLayoutStaticImportsSeparately().setSelected(settings.LAYOUT_STATIC_IMPORTS_SEPARATELY);

    final JBTable importLayoutTable = myImportLayoutPanel.getImportLayoutTable();
    AbstractTableModel model = (AbstractTableModel)importLayoutTable.getModel();
    model.fireTableDataChanged();

    model = (AbstractTableModel)myPackageTable.getModel();
    model.fireTableDataChanged();

    if (importLayoutTable.getRowCount() > 0) {
      importLayoutTable.getSelectionModel().setSelectionInterval(0, 0);
    }
    if (myPackageTable.getRowCount() > 0) {
      myPackageTable.getSelectionModel().setSelectionInterval(0, 0);
    }
  }

  public void reset() {
    reset(mySettings);
  }

  public void apply(CodeStyleSettings settings) {
    stopTableEditing();

    settings.LAYOUT_STATIC_IMPORTS_SEPARATELY = myImportLayoutPanel.areStaticImportsEnabled();
    settings.USE_FQ_CLASS_NAMES = myCbUseFQClassNames.isSelected();
    settings.USE_SINGLE_CLASS_IMPORTS = myCbUseSingleClassImports.isSelected();
    settings.INSERT_INNER_CLASS_IMPORTS = myCbInsertInnerClassImports.isSelected();
    try {
      settings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = Integer.parseInt(myClassCountField.getText());
    }
    catch (NumberFormatException e) {
      //just a bad number
    }
    try {
      settings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND = Integer.parseInt(myNamesCountField.getText());
    }
    catch (NumberFormatException e) {
      //just a bad number
    }

    final PackageEntryTable list = myImportLayoutPanel.getImportLayoutList();
    list.removeEmptyPackages();
    settings.IMPORT_LAYOUT_TABLE.copyFrom(list);

    myPackageList.removeEmptyPackages();
    settings.PACKAGES_TO_USE_IMPORT_ON_DEMAND.copyFrom(myPackageList);

    myFqnInJavadocOption.apply(settings);
  }

  public void apply() {
    apply(mySettings);
  }

  private void stopTableEditing() {
    TableUtil.stopEditing(myImportLayoutPanel.getImportLayoutTable());
    TableUtil.stopEditing(myPackageTable);
  }

  public boolean isModified(CodeStyleSettings settings) {
    boolean isModified = isModified(myImportLayoutPanel.getCbLayoutStaticImportsSeparately(), settings.LAYOUT_STATIC_IMPORTS_SEPARATELY);
    isModified |= isModified(myCbUseFQClassNames, settings.USE_FQ_CLASS_NAMES);
    isModified |= myFqnInJavadocOption.isModified(settings);
    isModified |= isModified(myCbUseSingleClassImports, settings.USE_SINGLE_CLASS_IMPORTS);
    isModified |= isModified(myCbInsertInnerClassImports, settings.INSERT_INNER_CLASS_IMPORTS);
    isModified |= isModified(myClassCountField, settings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND);
    isModified |= isModified(myNamesCountField, settings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND);

    isModified |= isModified(myImportLayoutPanel.getImportLayoutList(), settings.IMPORT_LAYOUT_TABLE);
    isModified |= isModified(myPackageList, settings.PACKAGES_TO_USE_IMPORT_ON_DEMAND);

    return isModified;
  }

  public boolean isModified() {
    return isModified(mySettings);
  }

  private static boolean isModified(JTextField textField, int value) {
    try {
      int fieldValue = Integer.parseInt(textField.getText().trim());
      return fieldValue != value;
    }
    catch (NumberFormatException e) {
      return false;
    }
  }

  private static boolean isModified(JCheckBox checkBox, boolean value) {
    return checkBox.isSelected() != value;
  }

  private static boolean isModified(PackageEntryTable list, PackageEntryTable table) {
    if (list.getEntryCount() != table.getEntryCount()) {
      return true;
    }

    for (int i = 0; i < list.getEntryCount(); i++) {
      PackageEntry entry1 = list.getEntryAt(i);
      PackageEntry entry2 = table.getEntryAt(i);
      if (!entry1.equals(entry2)) {
        return true;
      }
    }

    return false;
  }
}

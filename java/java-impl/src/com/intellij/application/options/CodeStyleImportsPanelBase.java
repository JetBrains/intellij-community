/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.psi.codeStyle.ImportsLayoutSettings;
import com.intellij.psi.codeStyle.PackageEntry;
import com.intellij.psi.codeStyle.PackageEntryTable;
import com.intellij.ui.OptionGroup;
import com.intellij.ui.TableUtil;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;

public abstract class CodeStyleImportsPanelBase extends JPanel {
  private final PackageEntryTable myPackageList = new PackageEntryTable();
  private JCheckBox myCbUseFQClassNames;
  private JCheckBox myCbUseSingleClassImports;
  private JCheckBox myCbInsertInnerClassImports;
  private JTextField myClassCountField;
  private JTextField myNamesCountField;
  private JBTable myPackageTable;

  private JPanel myGeneralPanel;
  private JPanel myPackagesPanel;
  private JPanel myImportsLayoutPanel;
  private JPanel myWholePanel;
  private ImportLayoutPanel myImportLayoutPanel;
  private FullyQualifiedNamesInJavadocOptionProvider myFqnInJavadocOption;

  public CodeStyleImportsPanelBase() {
    setLayout(new BorderLayout());
    add(myWholePanel, BorderLayout.CENTER);

    myGeneralPanel.add(createGeneralOptionsPanel(), BorderLayout.CENTER);
    createImportPanel();
    createPackagePanel();
  }

  public abstract void reset(CodeStyleSettings settings);

  public abstract void apply(CodeStyleSettings settings);

  public abstract boolean isModified(CodeStyleSettings settings);

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

    fillCustomOptions(group);

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

  public void resetLayoutSettings(ImportsLayoutSettings settings) {
    myCbUseFQClassNames.setSelected(settings.isUseFqClassNames());
    myCbUseSingleClassImports.setSelected(settings.isUseSingleClassImports());
    myCbInsertInnerClassImports.setSelected(settings.isInsertInnerClassImports());
    myClassCountField.setText(Integer.toString(settings.getClassCountToUseImportOnDemand()));
    myNamesCountField.setText(Integer.toString(settings.getNamesCountToUseImportOnDemand()));

    myImportLayoutPanel.getImportLayoutList().copyFrom(settings.getImportLayoutTable());
    myPackageList.copyFrom(settings.getPackagesToUseImportOnDemand());

    myImportLayoutPanel.getCbLayoutStaticImportsSeparately().setSelected(settings.isLayoutStaticImportsSeparately());

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

  public void applyLayoutSettings(ImportsLayoutSettings settings) {
    stopTableEditing();

    settings.setLayoutStaticImportsSeparately(myImportLayoutPanel.areStaticImportsEnabled());
    settings.setUseFqClassNames(myCbUseFQClassNames.isSelected());
    settings.setUseSingleClassImports(myCbUseSingleClassImports.isSelected());
    settings.setInsertInnerClassImports(myCbInsertInnerClassImports.isSelected());
    try {
      int value = Integer.parseInt(myClassCountField.getText());
      settings.setClassCountToUseImportOnDemand(value);
    }
    catch (NumberFormatException e) {
      //just a bad number
    }
    try {
      int value = Integer.parseInt(myNamesCountField.getText());
      settings.setNamesCountToUseImportOnDemand(value);
    }
    catch (NumberFormatException e) {
      //just a bad number
    }

    PackageEntryTable list = myImportLayoutPanel.getImportLayoutList();
    settings.getImportLayoutTable().copyFrom(getCopyWithoutEmptyPackages(list));
    settings.getPackagesToUseImportOnDemand().copyFrom(getCopyWithoutEmptyPackages(myPackageList));
  }

  public boolean isModifiedLayoutSettings(ImportsLayoutSettings settings) {
    boolean isModified = isModified(myImportLayoutPanel.getCbLayoutStaticImportsSeparately(), settings.isLayoutStaticImportsSeparately());
    isModified |= isModified(myCbUseFQClassNames, settings.isUseFqClassNames());
    isModified |= isModified(myCbUseSingleClassImports, settings.isUseSingleClassImports());
    isModified |= isModified(myCbInsertInnerClassImports, settings.isInsertInnerClassImports());
    isModified |= isModified(myClassCountField, settings.getClassCountToUseImportOnDemand());
    isModified |= isModified(myNamesCountField, settings.getNamesCountToUseImportOnDemand());

    PackageEntryTable list = myImportLayoutPanel.getImportLayoutList();
    isModified |= isModified(getCopyWithoutEmptyPackages(list), settings.getImportLayoutTable());
    isModified |= isModified(getCopyWithoutEmptyPackages(myPackageList), settings.getPackagesToUseImportOnDemand());

    return isModified;
  }

  private void stopTableEditing() {
    TableUtil.stopEditing(myImportLayoutPanel.getImportLayoutTable());
    TableUtil.stopEditing(myPackageTable);
  }

  protected void fillCustomOptions(OptionGroup group) {
  }

  @NotNull
  private static PackageEntryTable getCopyWithoutEmptyPackages(PackageEntryTable table) {
    try {
      PackageEntryTable copy = (PackageEntryTable)table.clone();
      copy.removeEmptyPackages();
      return copy;
    }
    catch (CloneNotSupportedException ignored) {
      throw new IllegalStateException("Clone should be supported");
    }
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

  protected static boolean isModified(JCheckBox checkBox, boolean value) {
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
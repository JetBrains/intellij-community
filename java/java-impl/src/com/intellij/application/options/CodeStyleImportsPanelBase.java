// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options;

import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.ImportsLayoutSettings;
import com.intellij.psi.codeStyle.PackageEntry;
import com.intellij.psi.codeStyle.PackageEntryTable;
import com.intellij.ui.TableUtil;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;

public abstract class CodeStyleImportsPanelBase extends JPanel {
  private final PackageEntryTable myPackageList = new PackageEntryTable();
  private final CodeStyleImportsBaseUI kotlinUI;
  private final JBTable myPackageTable;
  private final ImportLayoutPanel myImportLayoutPanel;

  public CodeStyleImportsPanelBase() {
    myImportLayoutPanel = new ImportLayoutPanel() {
      @Override
      public void refresh() {
        refreshTable(myPackageTable, myPackageList);
        refreshTable(getImportLayoutTable(), getImportLayoutList());
      }
    };

    myPackageTable = ImportLayoutPanel.createTableForPackageEntries(myPackageList, myImportLayoutPanel);

    kotlinUI = createKotlinUI(PackagePanel.createPackagesPanel(myPackageTable, myPackageList), myImportLayoutPanel);

    setLayout(new BorderLayout());
    add(new JBScrollPane(kotlinUI.panel), BorderLayout.CENTER);
  }

  public abstract void reset(CodeStyleSettings settings);

  public abstract void apply(CodeStyleSettings settings);

  public abstract boolean isModified(CodeStyleSettings settings);

  protected CodeStyleImportsBaseUI createKotlinUI(JComponent packages, JComponent importLayout) {
    CodeStyleImportsBaseUI result = new CodeStyleImportsBaseUI(packages, importLayout);
    result.init();
    return result;
  }

  private void refreshTable(final JBTable table, final PackageEntryTable packageTable) {
    AbstractTableModel model = (AbstractTableModel)table.getModel();
    table.createDefaultColumnsFromModel();
    model.fireTableDataChanged();
    ImportLayoutPanel.resizeColumns(packageTable, table, myImportLayoutPanel.areStaticImportsEnabled());
  }

  public void resetLayoutSettings(ImportsLayoutSettings settings) {
    kotlinUI.reset(settings);

    myImportLayoutPanel.getImportLayoutList().copyFrom(settings.getImportLayoutTable());
    myPackageList.copyFrom(settings.getPackagesToUseImportOnDemand());

    myImportLayoutPanel.getCbLayoutStaticImportsSeparately().setSelected(settings.isLayoutStaticImportsSeparately());
    myImportLayoutPanel.getCbLayoutOnDemandImportsFromSamePackageFirst().setSelected(settings.isLayoutOnDemandImportFromSamePackageFirst());

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
    settings.setLayoutOnDemandImportFromSamePackageFirst(myImportLayoutPanel.isLayoutOnDemandImportsFromSamePackageFirst());
    kotlinUI.apply(settings);
    PackageEntryTable list = myImportLayoutPanel.getImportLayoutList();
    settings.getImportLayoutTable().copyFrom(getCopyWithoutEmptyPackages(list));
    settings.getPackagesToUseImportOnDemand().copyFrom(getCopyWithoutEmptyPackages(myPackageList));
  }

  public boolean isModifiedLayoutSettings(ImportsLayoutSettings settings) {
    boolean isModified = isModified(myImportLayoutPanel.getCbLayoutStaticImportsSeparately(), settings.isLayoutStaticImportsSeparately());
    isModified |= isModified(myImportLayoutPanel.getCbLayoutOnDemandImportsFromSamePackageFirst(), settings.isLayoutOnDemandImportFromSamePackageFirst());
    isModified |= kotlinUI.isModified(settings);

    PackageEntryTable list = myImportLayoutPanel.getImportLayoutList();
    isModified |= isModified(getCopyWithoutEmptyPackages(list), settings.getImportLayoutTable());
    isModified |= isModified(getCopyWithoutEmptyPackages(myPackageList), settings.getPackagesToUseImportOnDemand());

    return isModified;
  }

  private void stopTableEditing() {
    TableUtil.stopEditing(myImportLayoutPanel.getImportLayoutTable());
    TableUtil.stopEditing(myPackageTable);
  }

  private static @NotNull PackageEntryTable getCopyWithoutEmptyPackages(PackageEntryTable table) {
    try {
      PackageEntryTable copy = (PackageEntryTable)table.clone();
      copy.removeEmptyPackages();
      return copy;
    }
    catch (CloneNotSupportedException ignored) {
      throw new IllegalStateException("Clone should be supported");
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
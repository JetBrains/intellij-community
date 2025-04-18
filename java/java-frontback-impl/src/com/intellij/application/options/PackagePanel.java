// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options;

import com.intellij.psi.codeStyle.PackageEntry;
import com.intellij.psi.codeStyle.PackageEntryTable;
import com.intellij.ui.TableUtil;
import com.intellij.ui.table.JBTable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;

import static com.intellij.application.options.PackagePanelUIKt.doCreatePackagesPanel;

/**
 * @author Max Medvedev
 */
public final class PackagePanel {

  static void addPackageToPackages(JBTable table, PackageEntryTable list) {
    int selected = table.getSelectedRow() + 1;
    if (selected < 0) {
      selected = list.getEntryCount();
    }
    PackageEntry entry = new PackageEntry(false, "", true);
    list.insertEntryAt(entry, selected);
    ImportLayoutPanel.refreshTableModel(selected, table);
  }

  static void removeEntryFromPackages(JBTable table, PackageEntryTable list) {
    int selected = table.getSelectedRow();
    if (selected < 0) return;
    TableUtil.stopEditing(table);
    list.removeEntryAt(selected);
    AbstractTableModel model = (AbstractTableModel)table.getModel();
    model.fireTableRowsDeleted(selected, selected);
    if (selected >= list.getEntryCount()) {
      selected--;
    }
    if (selected >= 0) {
      table.setRowSelectionInterval(selected, selected);
    }
  }

  public static JPanel createPackagesPanel(final JBTable packageTable, final PackageEntryTable packageList) {
    return doCreatePackagesPanel(packageTable, packageList);
  }
}

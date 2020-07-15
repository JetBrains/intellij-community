// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options;

import com.intellij.java.JavaBundle;
import com.intellij.psi.codeStyle.PackageEntry;
import com.intellij.psi.codeStyle.PackageEntryTable;
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;

/**
 * @author Max Medvedev
 */
public final class PackagePanel {

  private static void addPackageToPackages(JBTable table, PackageEntryTable list) {
    int selected = table.getSelectedRow() + 1;
    if (selected < 0) {
      selected = list.getEntryCount();
    }
    PackageEntry entry = new PackageEntry(false, "", true);
    list.insertEntryAt(entry, selected);
    ImportLayoutPanel.refreshTableModel(selected, table);
  }

  private static void removeEntryFromPackages(JBTable table, PackageEntryTable list) {
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
    JPanel panel = ToolbarDecorator.createDecorator(packageTable)
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          addPackageToPackages(packageTable, packageList);
        }
      }).setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          removeEntryFromPackages(packageTable, packageList);
        }
      }).disableUpDownActions().setPreferredSize(new Dimension(-1, 100)).createPanel();

    UIUtil.addBorder(panel, IdeBorderFactory.createTitledBorder(JavaBundle.message("title.packages.to.use.import.with"), false));

    return panel;
  }
}

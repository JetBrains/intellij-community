// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.impl.javaCompiler;

import com.intellij.compiler.options.ModuleOptionsTableModel;
import com.intellij.compiler.options.ModuleTableCellRenderer;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.project.Project;
import com.intellij.ui.InsertPathAction;
import com.intellij.ui.TableSpeedSearch;
import com.intellij.ui.TableUtil;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.fields.ExpandableTextField;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.GridBag;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 */
public class CompilerModuleOptionsComponent extends JPanel {
  private final JBTable myTable;
  private final Project myProject;

  public CompilerModuleOptionsComponent(@NotNull Project project) {
    super(new GridBagLayout());
    myProject = project;

    myTable = new JBTable(new ModuleOptionsTableModel());
    myTable.setShowGrid(false);
    myTable.setRowHeight(JBUIScale.scale(22));
    myTable.getEmptyText().setText(JavaCompilerBundle.message("settings.additional.compilation.options"));

    TableColumn moduleColumn = myTable.getColumnModel().getColumn(0);
    moduleColumn.setHeaderValue(JavaCompilerBundle.message("settings.override.module.column"));
    moduleColumn.setCellRenderer(new ModuleTableCellRenderer());

    TableColumn optionsColumn = myTable.getColumnModel().getColumn(1);
    String columnTitle = JavaCompilerBundle.message("settings.override.compilation.options.column");
    optionsColumn.setHeaderValue(columnTitle);
    int width = myTable.getFontMetrics(myTable.getFont()).stringWidth(columnTitle) + 10;
    optionsColumn.setPreferredWidth(width);
    optionsColumn.setMinWidth(width);
    ExpandableTextField editor = new ExpandableTextField();
    InsertPathAction.addTo(editor, null, false);
    optionsColumn.setCellEditor(new DefaultCellEditor(editor));
    TableSpeedSearch.installOn(myTable);

    JPanel table = ToolbarDecorator.createDecorator(myTable)
      .disableUpAction()
      .disableDownAction()
      .setAddAction(b -> addModules())
      .setRemoveAction(b -> removeSelectedModules())
      .createPanel();
    table.setPreferredSize(new Dimension(myTable.getWidth(), 150));
    JLabel header = new JLabel(JavaCompilerBundle.message("settings.override.compiler.parameters.per.module"));

    GridBag gridBag = new GridBag()
      .setDefaultAnchor(GridBagConstraints.WEST)
      .setDefaultWeightX(1.0).setDefaultWeightY(1.0)
      .setDefaultInsets(6, 0, 0, 0);

    add(header, gridBag.nextLine().weighty(0.0));
    add(table, gridBag.nextLine().fillCell());
  }

  private void addModules() {
    int i = ((ModuleOptionsTableModel)myTable.getModel()).addModulesToModel(myProject, this);
    if (i >= 0) {
      TableUtil.selectRows(myTable, new int[]{i});
      TableUtil.scrollSelectionToVisible(myTable);
    }
  }

  private void removeSelectedModules() {
    if (myTable.getSelectedRows().length > 0) {
      TableUtil.removeSelectedItems(myTable);
    }
  }

  public @NotNull Map<String, String> getModuleOptionsMap() {
    return ((ModuleOptionsTableModel)myTable.getModel()).getModuleOptions();
  }

  public void setModuleOptionsMap(@NotNull Map<String, String> moduleOptions) {
    ((ModuleOptionsTableModel)myTable.getModel()).setModuleOptions(myProject, moduleOptions);
  }
}
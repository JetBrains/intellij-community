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
package com.intellij.compiler.impl.javaCompiler;

import com.intellij.compiler.options.ModuleTableCellRenderer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ChooseModulesDialog;
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.ItemRemovable;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
public class CompilerModuleOptionsComponent extends JPanel {

  private final JBTable myTable;
  private final Project myProject;

  public CompilerModuleOptionsComponent(Project project) {
    super(new GridBagLayout());
    myProject = project;

    myTable = new JBTable(new MyTableModel());
    myTable.setRowHeight(22);
    myTable.getEmptyText().setText("Additional compilation options will be the same for all modules");

    final TableColumn moduleColumn = myTable.getColumnModel().getColumn(0);
    moduleColumn.setHeaderValue("Module");
    moduleColumn.setCellRenderer(new ModuleTableCellRenderer());

    final TableColumn targetLevelColumn = myTable.getColumnModel().getColumn(1);
    final String columnTitle = "Compilation options";
    targetLevelColumn.setHeaderValue(columnTitle);
    final int width = myTable.getFontMetrics(myTable.getFont()).stringWidth(columnTitle) + 10;
    targetLevelColumn.setPreferredWidth(width);
    targetLevelColumn.setMinWidth(width);
    //targetLevelColumn.setMaxWidth(width);
    new TableSpeedSearch(myTable);

    final JPanel table = ToolbarDecorator.createDecorator(myTable)
      .disableUpAction()
      .disableDownAction()
      .setAddAction(new ChooseModuleAction())
      .setRemoveAction(new RemoveSelectedRowsAction())
      .createPanel();
    table.setPreferredSize(new Dimension(myTable.getWidth(), 150));
    final JLabel header = new JLabel("Override compiler parameters per-module:");

    add(header, new GridBagConstraints(0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, JBUI.insets(5, 5, 0, 0), 0, 0));
    add(table, new GridBagConstraints(0, 1, 1, 1, 1.0, 1.0, GridBagConstraints.WEST, GridBagConstraints.BOTH, JBUI.insets(5, 5, 0, 0), 0, 0));
  }

  @NotNull
  public Map<String, String> getModuleOptionsMap() {
    MyTableModel model = (MyTableModel)myTable.getModel();
    final Map<String, String> map = new HashMap<>();
    for (MyTableModel.Item item : model.getItems()) {
      map.put(item.module.getName(), item.options);
    }
    return map;
  }

  public void setModuleOptionsMap(@NotNull Map<String, String> moduleOptions) {
    final Map<Module, String> map;
    if (!moduleOptions.isEmpty()) {
      map = new HashMap<>();
      for (Module module : ModuleManager.getInstance(myProject).getModules()) {
        final String options = moduleOptions.get(module.getName());
        if (options != null) {
          map.put(module, options);
        }
      }
    }
    else {
      map = Collections.emptyMap();
    }
    ((MyTableModel)myTable.getModel()).setItems(map);
  }

  private static final class MyTableModel extends AbstractTableModel implements ItemRemovable{
    private final List<Item> myItems = new ArrayList<>();
    @Override
    public int getRowCount() {
      return myItems.size();
    }

    @Override
    public int getColumnCount() {
      return 2;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return columnIndex != 0;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      final Item item = myItems.get(rowIndex);
      return columnIndex == 0? item.module : item.options;
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      final Item item = myItems.get(rowIndex);
      item.options = ((String)aValue).trim();
      fireTableCellUpdated(rowIndex, columnIndex);
    }

    public void addItems(Collection<Module> modules)  {
      for (Module module : modules) {
        myItems.add(new Item(module));
      }
      sortItems();
      fireTableDataChanged();
    }

    private void sortItems() {
      Collections.sort(myItems, Comparator.comparing(item -> item.module.getName()));
    }

    public List<Item> getItems() {
      return myItems;
    }

    @Override
    public void removeRow(int idx) {
      myItems.remove(idx);
      fireTableRowsDeleted(idx, idx);
    }

    public void setItems(Map<Module, String> items) {
      myItems.clear();
      for (Map.Entry<Module, String> entry : items.entrySet()) {
        myItems.add(new Item(entry.getKey(), entry.getValue()));
      }
      sortItems();
      fireTableDataChanged();
    }

    public int getModuleRow(Module module) {
      for (int i = 0; i < myItems.size(); i++) {
        if (myItems.get(i).module.equals(module)) {
          return i;
        }
      }
      return -1;
    }

    private static final class Item {
      final Module module;
      String options = "";

      Item(Module module) {
        this.module = module;
      }

      Item(Module module, String options) {
        this.module = module;
        this.options = options;
      }
    }
  }

  private class ChooseModuleAction implements AnActionButtonRunnable {
    @Override
    public void run(AnActionButton anActionButton) {
      final MyTableModel model = (MyTableModel)myTable.getModel();
      final Set<Module> added = new HashSet<>();
      for (MyTableModel.Item item : model.getItems()) {
        added.add(item.module);
      }
      final List<Module> modulesToChoose = new ArrayList<>();
      for (Module module : ModuleManager.getInstance(myProject).getModules()) {
        if (!added.contains(module)) {
          modulesToChoose.add(module);
        }
      }
      Collections.sort(modulesToChoose, Comparator.comparing(Module::getName));
      final ChooseModulesDialog chooser = new ChooseModulesDialog(CompilerModuleOptionsComponent.this, modulesToChoose, "Choose module");
      chooser.show();
      final List<Module> chosen = chooser.getChosenElements();
      if (!chosen.isEmpty()) {
        model.addItems(chosen);
        int i = model.getModuleRow(chosen.get(0));
        if (i >= 0) {
          TableUtil.selectRows(myTable, new int[]{i});
          TableUtil.scrollSelectionToVisible(myTable);
        }
      }
    }
  }

  private class RemoveSelectedRowsAction implements AnActionButtonRunnable {
    @Override
    public void run(AnActionButton anActionButton) {
      final int[] rows = myTable.getSelectedRows();
      if (rows.length > 0) {
        TableUtil.removeSelectedItems(myTable);
      }
    }
  }
}

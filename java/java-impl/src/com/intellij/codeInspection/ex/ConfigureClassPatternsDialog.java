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
package com.intellij.codeInspection.ex;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.ItemRemovable;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

class ConfigureClassPatternsDialog extends DialogWrapper {

  private final List<EntryPointsManagerBase.ClassPattern> myModifiedPatterns;
  private final List<EntryPointsManagerBase.ClassPattern> myPatterns;
  private final Project myProject;
  public ConfigureClassPatternsDialog(List<EntryPointsManagerBase.ClassPattern> patterns, Project project) {
    super(project);
    myModifiedPatterns = new ArrayList<>(patterns);
    myPatterns = patterns;
    myProject = project;
    init();
    setTitle("Configure Class Patterns");
  }
  @Override
  protected JComponent createCenterPanel() {
    final JBTable table = createTableForPatterns();
    final ToolbarDecorator toolbarDecorator = ToolbarDecorator.createDecorator(table)
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          myModifiedPatterns.add(new EntryPointsManagerBase.ClassPattern());
          AbstractTableModel model = (AbstractTableModel)table.getModel();
          final int row = myModifiedPatterns.size() - 1;
          model.fireTableRowsInserted(row, row);
          table.setRowSelectionInterval(row, row);
          table.editCellAt(row, 1);
        }
      }).setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          TableUtil.removeSelectedItems(table);
          table.repaint();
        }
      })
      .setRemoveActionUpdater(new AnActionButtonUpdater() {
        @Override
        public boolean isEnabled(AnActionEvent e) {
          return table.getSelectedRow() >= 0;
        }
      })
      .setButtonComparator("Add", "Remove");
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(SeparatorFactory.createSeparator("Mark class as entry point if name matches", null), BorderLayout.NORTH);
    panel.add(toolbarDecorator.createPanel(), BorderLayout.CENTER);
    return panel;
  }

  @Override
  protected void doOKAction() {
    myPatterns.clear();
    myPatterns.addAll(myModifiedPatterns);
    DaemonCodeAnalyzer.getInstance(myProject).restart();
    super.doOKAction();
  }

  private JBTable createTableForPatterns() {
    TableModel dataModel = new MyTableModel();

    final JBTable result = new JBTable(dataModel);
    result.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    TableCellEditor editor = result.getDefaultEditor(String.class);
    if (editor instanceof DefaultCellEditor) {
      ((DefaultCellEditor)editor).setClickCountToStart(1);
    }

    final TableColumn column = result.getTableHeader().getColumnModel().getColumn(0);
    column.setResizable(false);
    final int width = 15 + result.getTableHeader().getFontMetrics(result.getTableHeader().getFont()).stringWidth(result.getColumnName(0));
    column.setMaxWidth(width);
    column.setMinWidth(width);

    return result;
  }

  private class MyTableModel extends AbstractTableModel implements ItemRemovable {
    private final String[] myNames;

    public MyTableModel() {
      myNames = new String[] {"With Subclasses",  "Class"};
    }

    public int getColumnCount() {
      return 2;
    }

    public int getRowCount() {
      return myModifiedPatterns.size();
    }

    @Nullable
    public Object getValueAt(int row, int col) {
      if (row < 0) return null;
      final EntryPointsManagerBase.ClassPattern classPattern = myModifiedPatterns.get(row);
      if (classPattern == null) return null;
      if (col == 0) {
        return classPattern.hierarchically;
      }
      return classPattern.pattern;
    }

    public String getColumnName(int column) {
      return myNames[column];
    }

    public Class getColumnClass(int col) {
      if (col == 0) {
        return Boolean.class;
      }
      if (col == 1) {
        return String.class;
      }
      throw new IllegalArgumentException(String.valueOf(col));
    }

    public boolean isCellEditable(int row, int col) {
      return true;
    }

    public void setValueAt(Object aValue, int row, int col) {
      EntryPointsManagerBase.ClassPattern classPattern = myModifiedPatterns.get(row);
      if (classPattern == null) return;
      if (col == 0) {
        classPattern.hierarchically = (boolean)aValue;
      }
      else {
        classPattern.pattern = (String)aValue;
      }
      fireTableRowsUpdated(row, row);
    }

    @Override
    public void removeRow(int idx) {
      myModifiedPatterns.remove(idx);
    }
  }
}

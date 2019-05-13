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

import com.intellij.ide.DataManager;
import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.ex.MultiLineLabel;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.SeparatorFactory;
import com.intellij.ui.TableUtil;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.ItemRemovable;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.List;

class ClassPatternsPanel extends JPanel {

  private final List<EntryPointsManagerBase.ClassPattern> myModifiedPatterns;
  private final JBTable myTable;

  ClassPatternsPanel(List<EntryPointsManagerBase.ClassPattern> patterns) {
    super(new BorderLayout());
    myModifiedPatterns = patterns;
    myTable = createTableForPatterns();
    final String addClassMessage = "Add Class";
    final ToolbarDecorator toolbarDecorator = ToolbarDecorator.createDecorator(myTable)
      .setAddAction(button -> {
        Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(myTable));
        if (project == null) project = ProjectManager.getInstance().getDefaultProject();
        TreeClassChooser chooser = TreeClassChooserFactory.getInstance(project)
          .createWithInnerClassesScopeChooser(addClassMessage, GlobalSearchScope.allScope(project), ClassFilter.ALL, null);
        chooser.showDialog();
        final PsiClass selected = chooser.getSelected();
        if (selected != null) {
          insertRow(selected.getQualifiedName());
        }
      })
      .setAddActionName(addClassMessage)
      .setRemoveAction(button -> {
        TableUtil.removeSelectedItems(myTable);
        myTable.repaint();
      })
      .setRemoveActionUpdater(e -> myTable.getSelectedRow() >= 0);
    add(SeparatorFactory.createSeparator("Mark code as entry point if qualified name matches", null), BorderLayout.NORTH);
    add(toolbarDecorator.createPanel(), BorderLayout.CENTER);
    add(new MultiLineLabel("Leave method blank to represent constructors\n" +
                           "Any * will match against one or more characters in the qualified name (including dots)"), BorderLayout.SOUTH);
    setPreferredSize(new JBDimension(-1, 250));
  }

  private void insertRow(String pattern) {
    EntryPointsManagerBase.ClassPattern classPattern = new EntryPointsManagerBase.ClassPattern();
    classPattern.pattern = pattern;
    myModifiedPatterns.add(classPattern);
    AbstractTableModel model = (AbstractTableModel)myTable.getModel();
    final int row = myModifiedPatterns.size() - 1;
    model.fireTableRowsInserted(row, row);
    myTable.setRowSelectionInterval(row, row);
  }

  private JBTable createTableForPatterns() {
    TableModel dataModel = new MyTableModel();

    final JBTable result = new JBTable(dataModel);
    result.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    result.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
      @Override
      public Component getTableCellRendererComponent(JTable table,
                                                     Object value,
                                                     boolean isSelected,
                                                     boolean hasFocus,
                                                     int row,
                                                     int column) {
        final Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        if (value instanceof String && ((String)value).isEmpty()) {
          setText("constructors");
          setForeground(UIUtil.getInactiveTextColor());
        }
        else if (value instanceof String) {
          setText((String)value);
          setForeground(UIUtil.getTableForeground(isSelected));
        }
        return component;
      }
    });

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

  public String getValidationError(Project project) {
    TableUtil.stopEditing(myTable);
    final PsiNameHelper nameHelper = PsiNameHelper.getInstance(project);
    final ClassPatternValidator validator = new ClassPatternValidator(nameHelper);
    for (EntryPointsManagerBase.ClassPattern pattern : myModifiedPatterns) {
      final String errorText = validator.getErrorText(pattern.pattern);
      if (errorText != null) {
        return errorText;
      }

      final String subst = pattern.method.replace("*", "");
      if (!subst.isEmpty()) {
        if (!nameHelper.isIdentifier(subst)) {
          return "Method pattern '" + pattern.method + "' must be a valid java identifier, only '*' are accepted as placeholders";
        }
      }
    }
    return null;
  }

  private static class ClassPatternValidator implements InputValidatorEx {
    public static final String ERROR_MESSAGE = "Pattern must be a valid java qualified name, only '*' are accepted as placeholders";
    private final PsiNameHelper myNameHelper;

    ClassPatternValidator(PsiNameHelper nameHelper) {
      myNameHelper = nameHelper;
    }

    @Nullable
    @Override
    public String getErrorText(String inputString) {
      if (inputString.startsWith(".")) return ERROR_MESSAGE;
      final String qName = inputString.replace("*", "").replace(".", "");
      return !StringUtil.isEmpty(qName) && !myNameHelper.isQualifiedName(qName) ? ERROR_MESSAGE : null;
    }

    @Override
    public boolean checkInput(String inputString) {
      return getErrorText(inputString) == null;
    }

    @Override
    public boolean canClose(String inputString) {
      return getErrorText(inputString) == null;
    }
  }

  private class MyTableModel extends AbstractTableModel implements ItemRemovable {
    private final String[] myNames;

    MyTableModel() {
      myNames = new String[] {"With Subclasses",  "Class", "Method"};
    }

    @Override
    public int getColumnCount() {
      return 3;
    }

    @Override
    public int getRowCount() {
      return myModifiedPatterns.size();
    }

    @Override
    @Nullable
    public Object getValueAt(int row, int col) {
      if (row < 0 || row > myModifiedPatterns.size() - 1) return null;
      final EntryPointsManagerBase.ClassPattern classPattern = myModifiedPatterns.get(row);
      if (classPattern == null) return null;
      if (col == 0) {
        return classPattern.hierarchically;
      }
      if (col == 1) {
        return classPattern.pattern;
      }
      return classPattern.method;
    }

    @Override
    public String getColumnName(int column) {
      return myNames[column];
    }

    @Override
    public Class getColumnClass(int col) {
      if (col == 0) {
        return Boolean.class;
      }
      if (col == 1) {
        return String.class;
      }
      if (col == 2) {
        return String.class;
      }
      throw new IllegalArgumentException(String.valueOf(col));
    }

    @Override
    public boolean isCellEditable(int row, int col) {
      return true;
    }

    @Override
    public void setValueAt(Object aValue, int row, int col) {
      EntryPointsManagerBase.ClassPattern classPattern = myModifiedPatterns.get(row);
      if (classPattern == null) return;
      if (col == 0) {
        classPattern.hierarchically = (boolean)aValue;
      }
      else if (col == 1){
        classPattern.pattern = (String)aValue;
      }
      else {
        classPattern.method = (String)aValue;
      }
      fireTableRowsUpdated(row, row);
    }

    @Override
    public void removeRow(int idx) {
      myModifiedPatterns.remove(idx);
      fireTableRowsDeleted(idx, idx);
    }
  }
}

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
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.ItemRemovable;
import com.intellij.util.ui.JBDimension;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import java.awt.*;
import java.util.List;

class ClassPatternsPanel extends JPanel {

  private final List<EntryPointsManagerBase.ClassPattern> myModifiedPatterns;

  public ClassPatternsPanel(List<EntryPointsManagerBase.ClassPattern> patterns) {
    super(new BorderLayout());
    myModifiedPatterns = patterns;
    final JBTable table = createTableForPatterns();
    final String addPatternMessage = "Add Class Name Pattern";
    final String addClassMessage = "Add Class";
    final ToolbarDecorator toolbarDecorator = ToolbarDecorator.createDecorator(table)
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(table));
          if (project == null) project = ProjectManager.getInstance().getDefaultProject();
          TreeClassChooser chooser = TreeClassChooserFactory.getInstance(project)
            .createWithInnerClassesScopeChooser(addClassMessage, GlobalSearchScope.allScope(project), ClassFilter.ALL, null);
          chooser.showDialog();
          final PsiClass selected = chooser.getSelected();
          if (selected != null) {
            insertRow(selected.getQualifiedName(), table);
          }
        }
      })
      .setAddActionName(addClassMessage)
      .setAddIcon(IconUtil.getAddClassIcon())
      .addExtraAction(new AnActionButton(addPatternMessage, IconUtil.getAddPatternIcon()) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          final PsiNameHelper nameHelper = PsiNameHelper.getInstance(e.getProject());
          String selectedPattern = Messages.showInputDialog("Pattern:", "Class Name Pattern", Messages.getQuestionIcon(), null,
                                                            new ClassPatternValidator(nameHelper));
          if (selectedPattern != null) {
            insertRow(selectedPattern, table);
          }
        }
      })
      .setRemoveAction(new AnActionButtonRunnable() {
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
      .setButtonComparator(addClassMessage, addPatternMessage, "Remove");
    add(SeparatorFactory.createSeparator("Mark class as entry point if qualified name matches", null), BorderLayout.NORTH);
    add(toolbarDecorator.createPanel(), BorderLayout.CENTER);
    setPreferredSize(new JBDimension(-1, 250));
  }

  private void insertRow(String pattern, JBTable table) {
    EntryPointsManagerBase.ClassPattern classPattern = new EntryPointsManagerBase.ClassPattern();
    classPattern.pattern = pattern;
    myModifiedPatterns.add(classPattern);
    AbstractTableModel model = (AbstractTableModel)table.getModel();
    final int row = myModifiedPatterns.size() - 1;
    model.fireTableRowsInserted(row, row);
    table.setRowSelectionInterval(row, row);
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

  private static class ClassPatternValidator implements InputValidatorEx {
    public static final String ERROR_MESSAGE = "Pattern must be a valid java qualified name, only '*' are accepted as placeholders";
    private final PsiNameHelper myNameHelper;

    public ClassPatternValidator(PsiNameHelper nameHelper) {
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
      if (row < 0 || row > myModifiedPatterns.size() - 1) return null;
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
      fireTableRowsDeleted(idx, idx);
    }
  }
}

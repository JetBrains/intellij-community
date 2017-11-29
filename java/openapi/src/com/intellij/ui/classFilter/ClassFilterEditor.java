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

/*
 * Class ClassFilterEditor
 * @author Jeka
 */
package com.intellij.ui.classFilter;

import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.util.IconUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ComponentWithEmptyText;
import com.intellij.util.ui.ItemRemovable;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class ClassFilterEditor extends JPanel implements ComponentWithEmptyText {
  private static final String IS_ACTIVE = "Is Active";
  private static final String INCLUDE_EXCLUDE = "Include/Exclude";
  protected JBTable myTable = null;
  protected FilterTableModel myTableModel = null;
  protected final Project myProject;
  private final ClassFilter myChooserFilter;
  @Nullable
  private final String myPatternsHelpId;
  private final boolean myExcludeAllowed;
  private String classDelimiter = "$";

  public ClassFilterEditor(Project project) {
    this(project, null);
  }

  public ClassFilterEditor(Project project, ClassFilter classFilter) {
    this(project, classFilter, null);
  }

  public ClassFilterEditor(Project project, ClassFilter classFilter, @Nullable String patternsHelpId) {
    this(project, classFilter, patternsHelpId, false);
  }

  public ClassFilterEditor(Project project, ClassFilter classFilter, @Nullable String patternsHelpId, boolean excludeAllowed) {
    super(new BorderLayout());
    myPatternsHelpId = patternsHelpId;
    myExcludeAllowed = excludeAllowed;
    myTable = new JBTable();

    final ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myTable)
      .addExtraAction(new AnActionButton(getAddButtonText(), getAddButtonIcon()) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          addClassFilter();
        }

        @Override
        public void updateButton(AnActionEvent e) {
          super.updateButton(e);
          setEnabled(!myProject.isDefault());
        }
      });
    if (addPatternButtonVisible()) {
      decorator.addExtraAction(new AnActionButton(getAddPatternButtonText(), getAddPatternButtonIcon()) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          addPatternFilter();
        }

        @Override
        public void updateButton(AnActionEvent e) {
          super.updateButton(e);
          setEnabled(!myProject.isDefault());
        }
      });
    }
    add(decorator.setRemoveAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        TableUtil.removeSelectedItems(myTable);
      }
    }).setButtonComparator(getAddButtonText(), getAddPatternButtonText(), "Remove")
          .disableUpDownActions().createPanel(), BorderLayout.CENTER);

    myChooserFilter = classFilter;
    myProject = project;

    myTableModel = new FilterTableModel();
    myTable.setModel(myTableModel);
    myTable.setShowGrid(false);
    myTable.setIntercellSpacing(new Dimension(0, 0));
    myTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
    myTable.setColumnSelectionAllowed(false);
    myTable.setPreferredScrollableViewportSize(new Dimension(200, myTable.getRowHeight() * JBTable.PREFERRED_SCROLLABLE_VIEWPORT_HEIGHT_IN_ROWS));

    TableColumnModel columnModel = myTable.getColumnModel();
    TableColumn column = columnModel.getColumn(FilterTableModel.CHECK_MARK);
    int preferredWidth;
    if (!excludeAllowed) {
      myTable.setTableHeader(null);
      preferredWidth = 0;
    }
    else {
      JTableHeader tableHeader = myTable.getTableHeader();
      final FontMetrics fontMetrics = tableHeader.getFontMetrics(tableHeader.getFont());
      preferredWidth = fontMetrics.stringWidth(IS_ACTIVE) + 20;

      TableColumn includeColumn = columnModel.getColumn(FilterTableModel.INCLUDE_MARK);
      includeColumn.setCellRenderer(new EnabledCellRenderer(myTable.getDefaultRenderer(Boolean.class)));
      TableUtil.setupCheckboxColumn(includeColumn, fontMetrics.stringWidth(INCLUDE_EXCLUDE) + 20);
    }
    TableUtil.setupCheckboxColumn(column, preferredWidth);
    column.setCellRenderer(new EnabledCellRenderer(myTable.getDefaultRenderer(Boolean.class)));
    columnModel.getColumn(FilterTableModel.FILTER).setCellRenderer(new FilterCellRenderer());

    getEmptyText().setText(UIBundle.message("no.patterns"));
  }

  @NotNull
  @Override
  public StatusText getEmptyText() {
    return myTable.getEmptyText();
  }

  protected String getAddButtonText() {
    return UIBundle.message("button.add.class");
  }

  protected String getAddPatternButtonText() {
    return UIBundle.message("button.add.pattern");
  }

  protected Icon getAddButtonIcon() {
    return IconUtil.getAddClassIcon();
  }

  protected Icon getAddPatternButtonIcon() {
    return IconUtil.getAddPatternIcon();
  }

  protected boolean addPatternButtonVisible() {
    return true;
  }

  public void setFilters(com.intellij.ui.classFilter.ClassFilter[] filters) {
    myTableModel.setFilters(filters);
  }

  public com.intellij.ui.classFilter.ClassFilter[] getFilters() {
    return myTableModel.getFilters();
  }

  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    myTable.setEnabled(enabled);
    myTable.setRowSelectionAllowed(enabled);
    myTableModel.fireTableDataChanged();
  }

  public void stopEditing() {
    TableCellEditor editor = myTable.getCellEditor();
    if (editor != null) {
      editor.stopCellEditing();
    }
  }

  protected final class FilterTableModel extends AbstractTableModel implements ItemRemovable {
    private final List<com.intellij.ui.classFilter.ClassFilter> myFilters = new LinkedList<>();
    public static final int CHECK_MARK = 0;
    public static final int FILTER = 1;
    public static final int INCLUDE_MARK = 2;

    public final void setFilters(com.intellij.ui.classFilter.ClassFilter[] filters) {
      myFilters.clear();
      if (filters != null) {
        ContainerUtil.addAll(myFilters, filters);
      }
      fireTableDataChanged();
    }

    public com.intellij.ui.classFilter.ClassFilter[] getFilters() {
      for (Iterator<com.intellij.ui.classFilter.ClassFilter> it = myFilters.iterator(); it.hasNext(); ) {
        com.intellij.ui.classFilter.ClassFilter filter = it.next();
        String pattern = filter.getPattern();
        if (pattern == null || "".equals(pattern)) {
          it.remove();
        }
      }
      return myFilters.toArray(new com.intellij.ui.classFilter.ClassFilter[myFilters.size()]);
    }

    public com.intellij.ui.classFilter.ClassFilter getFilterAt(int index) {
      return myFilters.get(index);
    }

    public int getFilterIndex(com.intellij.ui.classFilter.ClassFilter filter) {
      return myFilters.indexOf(filter);
    }

    public void addRow(com.intellij.ui.classFilter.ClassFilter filter) {
      myFilters.add(filter);
      int row = myFilters.size() - 1;
      fireTableRowsInserted(row, row);
    }

    public int getRowCount() {
      return myFilters.size();
    }

    public int getColumnCount() {
      if (myExcludeAllowed) {
        return 3;
      }
      return 2;
    }

    @Override
    public String getColumnName(int column) {
      if (column == FILTER) {
        return "Pattern";
      }
      if (column == INCLUDE_MARK) {
        return INCLUDE_EXCLUDE;
      }
      return IS_ACTIVE;
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
      com.intellij.ui.classFilter.ClassFilter filter = myFilters.get(rowIndex);
      if (columnIndex == FILTER) {
        return filter;
      }
      if (columnIndex == CHECK_MARK) {
        return filter.isEnabled();
      }
      if (columnIndex == INCLUDE_MARK) {
        return filter.isInclude();
      }
      return null;
    }

    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      com.intellij.ui.classFilter.ClassFilter filter = myFilters.get(rowIndex);
      if (columnIndex == FILTER) {
        filter.setPattern(aValue != null ? aValue.toString() : "");
      }
      else if (columnIndex == CHECK_MARK) {
        filter.setEnabled(aValue == null || ((Boolean)aValue).booleanValue());
      }
      else if (columnIndex == INCLUDE_MARK) {
        filter.setInclude(aValue == null || ((Boolean)aValue).booleanValue());
      }
//      fireTableCellUpdated(rowIndex, columnIndex);
      fireTableRowsUpdated(rowIndex, rowIndex);
    }

    public Class getColumnClass(int columnIndex) {
      if (columnIndex == CHECK_MARK || columnIndex == INCLUDE_MARK) {
        return Boolean.class;
      }
      return super.getColumnClass(columnIndex);
    }

    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return isEnabled();
    }

    public void removeRow(final int idx) {
      myFilters.remove(idx);
      fireTableRowsDeleted(idx, idx);
    }
  }

  private class FilterCellRenderer extends DefaultTableCellRenderer {
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus, int row, int column) {
      Color color = UIUtil.getTableFocusCellBackground();
      UIManager.put(UIUtil.TABLE_FOCUS_CELL_BACKGROUND_PROPERTY, table.getSelectionBackground());
      Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      if (component instanceof JLabel) {
        ((JLabel)component).setBorder(noFocusBorder);
      }
      UIManager.put(UIUtil.TABLE_FOCUS_CELL_BACKGROUND_PROPERTY, color);
      com.intellij.ui.classFilter.ClassFilter filter =
        (com.intellij.ui.classFilter.ClassFilter)table.getValueAt(row, FilterTableModel.FILTER);
      component.setEnabled(isSelected || (ClassFilterEditor.this.isEnabled() && filter.isEnabled()));
      return component;
    }
  }

  private class EnabledCellRenderer extends DefaultTableCellRenderer {
    private final TableCellRenderer myDelegate;

    public EnabledCellRenderer(TableCellRenderer delegate) {
      myDelegate = delegate;
    }

    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus, int row, int column) {
      Component component = myDelegate.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      component.setEnabled(ClassFilterEditor.this.isEnabled());
      return component;
    }
  }

  @NotNull
  protected com.intellij.ui.classFilter.ClassFilter createFilter(String pattern) {
    return new com.intellij.ui.classFilter.ClassFilter(pattern);
  }

  protected void addPatternFilter() {
    ClassFilterEditorAddDialog dialog = new ClassFilterEditorAddDialog(myProject, myPatternsHelpId);
    if (dialog.showAndGet()) {
      String pattern = dialog.getPattern();
      if (pattern != null) {
        com.intellij.ui.classFilter.ClassFilter filter = createFilter(pattern);
        myTableModel.addRow(filter);
        int row = myTableModel.getRowCount() - 1;
        myTable.getSelectionModel().setSelectionInterval(row, row);
        myTable.scrollRectToVisible(myTable.getCellRect(row, 0, true));

        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
          IdeFocusManager.getGlobalInstance().requestFocus(myTable, true);
        });
      }
    }
  }

  protected void addClassFilter() {
    TreeClassChooser chooser = TreeClassChooserFactory.getInstance(myProject).createNoInnerClassesScopeChooser(
      UIBundle.message("class.filter.editor.choose.class.title"), GlobalSearchScope.allScope(myProject), myChooserFilter, null);
    chooser.showDialog();
    PsiClass selectedClass = chooser.getSelected();
    if (selectedClass != null) {
      com.intellij.ui.classFilter.ClassFilter filter = createFilter(getJvmClassName(selectedClass));
      myTableModel.addRow(filter);
      int row = myTableModel.getRowCount() - 1;
      myTable.getSelectionModel().setSelectionInterval(row, row);
      myTable.scrollRectToVisible(myTable.getCellRect(row, 0, true));

      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
        IdeFocusManager.getGlobalInstance().requestFocus(myTable, true);
      });
    }
  }

  @Nullable
  private String getJvmClassName(PsiClass aClass) {
    PsiClass parentClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class, true);
    if (parentClass != null) {
      final String parentName = getJvmClassName(parentClass);
      if (parentName == null) {
        return null;
      }
      return parentName + classDelimiter + aClass.getName();
    }
    return aClass.getQualifiedName();
  }

  public void setClassDelimiter(String classDelimiter) {
    this.classDelimiter = classDelimiter;
  }

  public void addPattern(String pattern) {
    com.intellij.ui.classFilter.ClassFilter filter = createFilter(pattern);
    myTableModel.addRow(filter);
  }
}

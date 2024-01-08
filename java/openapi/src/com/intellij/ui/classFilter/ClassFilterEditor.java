// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * Class ClassFilterEditor
 * @author Jeka
 */
package com.intellij.ui.classFilter;

import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.TableUtil;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.JBTable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.LinkedList;
import java.util.List;

public class ClassFilterEditor extends JPanel implements ComponentWithEmptyText {
  protected JBTable myTable;
  protected FilterTableModel myTableModel;
  protected final Project myProject;
  private final ClassFilter myChooserFilter;
  @Nullable
  private final String myPatternsHelpId;
  private String classDelimiter = "$";

  public ClassFilterEditor(Project project) {
    this(project, null);
  }

  public ClassFilterEditor(Project project, ClassFilter classFilter) {
    this(project, classFilter, null);
  }

  public ClassFilterEditor(Project project, ClassFilter classFilter, @Nullable String patternsHelpId) {
    super(new BorderLayout());
    myPatternsHelpId = patternsHelpId;
    myTable = new JBTable();

    final ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myTable);
    if (addPatternButtonVisible()) {
      DefaultActionGroup addGroup = new DefaultActionGroup(new AddClassFilterAction(), new AddPatternFilterAction());
      addGroup.getTemplatePresentation().setPopupGroup(true);
      addGroup.getTemplatePresentation().setIcon(AllIcons.General.Add);
      addGroup.getTemplatePresentation().setText(JavaBundle.messagePointer("button.add"));
      addGroup.registerCustomShortcutSet(CommonShortcuts.getNewForDialogs(), null);
      decorator.addExtraAction(addGroup);
    }
    else {
      decorator.addExtraAction(new AddClassFilterAction());
    }
    add(decorator.setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          TableUtil.removeSelectedItems(myTable);
        }
      })
          .setButtonComparator(getAddButtonText(), getAddPatternButtonText(), CommonBundle.message("button.remove"))
          .disableUpDownActions()
          .createPanel(), BorderLayout.CENTER);

    myChooserFilter = classFilter;
    myProject = project;

    myTableModel = new FilterTableModel();
    myTable.setModel(myTableModel);
    myTable.setShowGrid(false);
    myTable.setIntercellSpacing(new Dimension(0, 0));
    myTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
    myTable.setColumnSelectionAllowed(false);
    myTable.setPreferredScrollableViewportSize(JBUI.size(200, -1));
    myTable.setVisibleRowCount(JBTable.PREFERRED_SCROLLABLE_VIEWPORT_HEIGHT_IN_ROWS);

    TableColumnModel columnModel = myTable.getColumnModel();
    TableColumn column = columnModel.getColumn(FilterTableModel.CHECK_MARK);
    int preferredWidth;
    myTable.setTableHeader(null);
    preferredWidth = 0;
    TableUtil.setupCheckboxColumn(column, preferredWidth);
    column.setCellRenderer(new EnabledCellRenderer(myTable.getDefaultRenderer(Boolean.class)));
    columnModel.getColumn(FilterTableModel.FILTER).setCellRenderer(new FilterCellRenderer());

    getEmptyText().setText(JavaBundle.message("no.patterns"));
  }

  private class AddClassFilterAction extends DumbAwareAction {
    private AddClassFilterAction() {
      super(getAddButtonText(), null, getAddButtonIcon());
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(!myProject.isDefault());
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      addClassFilter();
    }
  }

  private class AddPatternFilterAction extends DumbAwareAction {
    private AddPatternFilterAction() {
      super(getAddPatternButtonText(), null, getAddPatternButtonIcon());
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(!myProject.isDefault());
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      addPatternFilter();
    }
  }

  @NotNull
  @Override
  public StatusText getEmptyText() {
    return myTable.getEmptyText();
  }

  protected @Nls String getAddButtonText() {
    return JavaBundle.message("button.add.class");
  }

  protected @Nls String getAddPatternButtonText() {
    return JavaBundle.message("button.add.pattern");
  }

  protected Icon getAddButtonIcon() {
    return AllIcons.Nodes.Class;
  }

  protected Icon getAddPatternButtonIcon() {
    return AllIcons.Actions.Regex;
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

  @Override
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

  public void setupEasyFocusTraversing() {
    myTable.setupEasyFocusTraversing();
  }

  protected final class FilterTableModel extends AbstractTableModel implements ItemRemovable {
    private final List<com.intellij.ui.classFilter.ClassFilter> myFilters = new LinkedList<>();
    public static final int CHECK_MARK = 0;
    public static final int FILTER = 1;

    private boolean myEditEnabled = true;

    public void setFilters(com.intellij.ui.classFilter.ClassFilter[] filters) {
      myFilters.clear();
      if (filters != null) {
        ContainerUtil.addAll(myFilters, filters);
      }
      fireTableDataChanged();
    }

    public com.intellij.ui.classFilter.ClassFilter[] getFilters() {
      myFilters.removeIf(filter -> StringUtil.isEmpty(filter.getPattern()));
      return myFilters.toArray(com.intellij.ui.classFilter.ClassFilter.EMPTY_ARRAY);
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

    @Override
    public int getRowCount() {
      return myFilters.size();
    }

    @Override
    public int getColumnCount() {
      return 2;
    }

    @Override
    public @NlsContexts.ColumnName String getColumnName(int column) {
      if (column == FILTER) {
        return JavaBundle.message("class.filter.editor.table.model.column.name.pattern");
      }
      return JavaBundle.message("class.filter.editor.table.model.column.name.isActive");
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      com.intellij.ui.classFilter.ClassFilter filter = myFilters.get(rowIndex);
      if (columnIndex == FILTER) {
        return filter;
      }
      if (columnIndex == CHECK_MARK) {
        return filter.isEnabled();
      }
      return null;
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      com.intellij.ui.classFilter.ClassFilter filter = myFilters.get(rowIndex);
      if (columnIndex == FILTER) {
        filter.setPattern(aValue != null ? aValue.toString() : "");
      }
      else if (columnIndex == CHECK_MARK) {
        filter.setEnabled(aValue == null || ((Boolean)aValue).booleanValue());
      }
//      fireTableCellUpdated(rowIndex, columnIndex);
      fireTableRowsUpdated(rowIndex, rowIndex);
    }

    @Override
    public Class getColumnClass(int columnIndex) {
      if (columnIndex == CHECK_MARK) {
        return Boolean.class;
      }
      return super.getColumnClass(columnIndex);
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return isEnabled() && (columnIndex != 1 || myEditEnabled);
    }

    @Override
    public void removeRow(final int idx) {
      myFilters.remove(idx);
      fireTableRowsDeleted(idx, idx);
    }

    public void setEditEnabled(boolean editEnabled) {
      myEditEnabled = editEnabled;
    }
  }

  private class FilterCellRenderer extends DefaultTableCellRenderer {
    @Override
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

    EnabledCellRenderer(TableCellRenderer delegate) {
      myDelegate = delegate;
    }

    @Override
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

        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myTable, true));
      }
    }
  }

  protected void addClassFilter() {
    TreeClassChooser chooser = TreeClassChooserFactory.getInstance(myProject).createNoInnerClassesScopeChooser(
      JavaBundle.message("class.filter.editor.choose.class.title"), GlobalSearchScope.allScope(myProject), myChooserFilter, null);
    chooser.showDialog();
    PsiClass selectedClass = chooser.getSelected();
    if (selectedClass != null) {
      com.intellij.ui.classFilter.ClassFilter filter = createFilter(getJvmClassName(selectedClass));
      myTableModel.addRow(filter);
      int row = myTableModel.getRowCount() - 1;
      myTable.getSelectionModel().setSelectionInterval(row, row);
      myTable.scrollRectToVisible(myTable.getCellRect(row, 0, true));

      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myTable, true));
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

/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TableUtil;
import com.intellij.ui.UIBundle;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.ItemRemovable;
import com.intellij.util.ui.Table;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class ClassFilterEditor extends JPanel {
  protected JTable myTable = null;
  protected FilterTableModel myTableModel = null;
  private JButton myAddClassButton;
  protected JButton myAddPatternButton;
  private JButton myRemoveButton;
  protected Project myProject;
  private TreeClassChooser.ClassFilter myChooserFilter;

  public ClassFilterEditor(Project project) {
    this (project, null);
  }

  public ClassFilterEditor(Project project, TreeClassChooser.ClassFilter classFilter) {
    super(new GridBagLayout());
    myAddClassButton = new JButton(getAddButtonText());
    myAddPatternButton = new JButton(getAddPatternButtonText());
    myRemoveButton = new JButton(UIBundle.message("button.remove"));
    myTable = new Table();
    JBScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTable);

    add(scrollPane, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 3, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(4, 4, 4, 6), 0, 0));
    add(myAddClassButton, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(4, 0, 0, 4), 0, 0));
    add(myAddPatternButton, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(4, 0, 0, 4), 0, 0));
    add(myRemoveButton, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(4, 0, 0, 4), 0, 0));

    myChooserFilter = classFilter;
    myProject = project;
    myAddClassButton.setDefaultCapable(false);
    myAddPatternButton.setDefaultCapable(false);
    myRemoveButton.setDefaultCapable(false);

    myTableModel = new FilterTableModel();
    myTable.setModel(myTableModel);
    myTable.setShowGrid(false);
    myTable.setIntercellSpacing(new Dimension(0, 0));
    myTable.setTableHeader(null);
    myTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
    myTable.setColumnSelectionAllowed(false);
    myTable.setPreferredScrollableViewportSize(new Dimension(200, 100));

    TableColumnModel columnModel = myTable.getColumnModel();
    TableColumn column = columnModel.getColumn(FilterTableModel.CHECK_MARK);
    int width = new JCheckBox().getPreferredSize().width;
    column.setPreferredWidth(width);
    column.setMaxWidth(width);
    column.setCellRenderer(new EnabledCellRenderer(myTable.getDefaultRenderer(Boolean.class)));
    columnModel.getColumn(FilterTableModel.FILTER).setCellRenderer(new FilterCellRenderer());

    myTable.registerKeyboardAction(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          myAddClassButton.doClick();
        }
      },
      KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, 0),
      JComponent.WHEN_FOCUSED
    );
    myTable.registerKeyboardAction(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          myRemoveButton.doClick();
        }
      },
      KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0),
      JComponent.WHEN_FOCUSED
    );
    myTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        myRemoveButton.setEnabled(myTable.getSelectedRow() > -1);
      }
    });

    myAddPatternButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        addPatternFilter();
      }
    });
    myAddClassButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        addClassFilter();
      }
    });

    myRemoveButton.addActionListener(new RemoveAction());
    myRemoveButton.setEnabled(false);
  }

  protected String getAddButtonText() {
    return UIBundle.message("button.add.class");
  }

  protected String getAddPatternButtonText() {
    return UIBundle.message("button.add.pattern");
  }

  public void setFilters(ClassFilter[] filters) {
    myTableModel.setFilters(filters);
  }

  public ClassFilter[] getFilters() {
    return myTableModel.getFilters();
  }

  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);

    myAddPatternButton.setEnabled(enabled);
    myAddClassButton.setEnabled(enabled);
    myRemoveButton.setEnabled((myTable.getSelectedRow() > -1) && enabled);
    myTable.setRowSelectionAllowed(enabled);
    myTableModel.fireTableDataChanged();
  }

  public void stopEditing() {
    TableCellEditor editor = myTable.getCellEditor();
    if (editor != null) {
      editor.stopCellEditing();
    }
  }

  protected final class FilterTableModel extends AbstractTableModel implements ItemRemovable{
    private final List<ClassFilter> myFilters = new LinkedList<ClassFilter>();
    public static final int CHECK_MARK = 0;
    public static final int FILTER = 1;

    public final void setFilters(ClassFilter[] filters) {
      myFilters.clear();
      if (filters != null) {
        myFilters.addAll(Arrays.asList(filters));
      }
      fireTableDataChanged();
    }

    public ClassFilter[] getFilters() {
      for (Iterator<ClassFilter> it = myFilters.iterator(); it.hasNext();) {
        ClassFilter filter = it.next();
        String pattern = filter.getPattern();
        if (pattern == null || "".equals(pattern)) {
          it.remove();
        }
      }
      return myFilters.toArray(new ClassFilter[myFilters.size()]);
    }

    public ClassFilter getFilterAt(int index) {
      return myFilters.get(index);
    }

    public int getFilterIndex(ClassFilter filter) {
      return myFilters.indexOf(filter);
    }

    public void addRow(ClassFilter filter) {
      myFilters.add(filter);
      int row = myFilters.size() - 1;
      fireTableRowsInserted(row, row);
    }

    public int getRowCount() {
      return myFilters.size();
    }

    public int getColumnCount() {
      return 2;
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
      ClassFilter filter = myFilters.get(rowIndex);
      if (columnIndex == FILTER) {
        return filter;
      }
      if (columnIndex == CHECK_MARK) {
        return filter.isEnabled()? Boolean.TRUE : Boolean.FALSE;
      }
      return null;
    }

    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      ClassFilter filter = myFilters.get(rowIndex);
      if (columnIndex == FILTER) {
        filter.setPattern(aValue != null? aValue.toString() : "");
      }
      else if (columnIndex == CHECK_MARK) {
        filter.setEnabled(aValue == null || ((Boolean)aValue).booleanValue());
      }
//      fireTableCellUpdated(rowIndex, columnIndex);
      fireTableRowsUpdated(rowIndex, rowIndex);
    }

    public Class getColumnClass(int columnIndex) {
      if (columnIndex == CHECK_MARK) {
        return Boolean.class;
      }
      return super.getColumnClass(columnIndex);
    }

    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return isEnabled() && (columnIndex == CHECK_MARK);
    }

    public void removeRow(final int idx) {
      myFilters.remove(idx);
      fireTableDataChanged();
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
      ClassFilter filter = (ClassFilter)table.getValueAt(row, FilterTableModel.FILTER);
      component.setEnabled(ClassFilterEditor.this.isEnabled() && filter.isEnabled());
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
  protected ClassFilter createFilter(String pattern){
    return new ClassFilter(pattern);
  }

  protected void addPatternFilter() {
    ClassFilterEditorAddDialog dialog = new ClassFilterEditorAddDialog(myProject);
    dialog.show();
    if (dialog.isOK()) {
      String pattern = dialog.getPattern();
      if (pattern != null) {
        ClassFilter filter = createFilter(pattern);
        myTableModel.addRow(filter);
        int row = myTableModel.getRowCount() - 1;
        myTable.getSelectionModel().setSelectionInterval(row, row);
        myTable.scrollRectToVisible(myTable.getCellRect(row, 0, true));

        myTable.requestFocus();
      }
    }
  }

  protected void addClassFilter() {
    TreeClassChooser chooser = TreeClassChooserFactory.getInstance(myProject).createNoInnerClassesScopeChooser(
      UIBundle.message("class.filter.editor.choose.class.title"), GlobalSearchScope.allScope(myProject), myChooserFilter, null);
    chooser.showDialog();
    PsiClass selectedClass = chooser.getSelectedClass();
    if (selectedClass != null) {
      ClassFilter filter = createFilter(getJvmClassName(selectedClass));
      myTableModel.addRow(filter);
      int row = myTableModel.getRowCount() - 1;
      myTable.getSelectionModel().setSelectionInterval(row, row);
      myTable.scrollRectToVisible(myTable.getCellRect(row, 0, true));

      myTable.requestFocus();
    }
  }

  @Nullable
  private static String getJvmClassName(PsiClass aClass) {
    PsiClass parentClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class, true);
    if(parentClass != null) {
      final String parentName = getJvmClassName(parentClass);
      if (parentName == null) {
        return null;
      }
      return parentName + "$" + aClass.getName();
    }
    return aClass.getQualifiedName();
  }

  public void addPattern(String pattern) {
    ClassFilter filter = createFilter(pattern);
    myTableModel.addRow(filter);
  }

  private final class RemoveAction implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      TableUtil.removeSelectedItems(myTable);
    }
  }
}

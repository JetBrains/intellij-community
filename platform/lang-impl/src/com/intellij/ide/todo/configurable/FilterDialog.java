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

package com.intellij.ide.todo.configurable;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.todo.TodoFilter;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.search.TodoPattern;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.Table;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.util.List;

/**
 * @author Vladimir Kondratyev
 */
class FilterDialog extends DialogWrapper {
  private final TodoFilter myFilter;
  private final int myFilterIndex;
  private final List<TodoPattern> myPatterns;
  private final List<TodoFilter> myFilters;

  private final JTextField myNameField;
  private final Table myTable;

  /**
   * @param parent      parent component.
   * @param filter      filter to be edited.
   * @param filterIndex index of <code>filter</code> in the <code>filters</code>. This parameter is
   *                    needed to not compare filter with itself when validating.
   * @param filters     all already configured filters. This parameter is used to
   * @param patterns    all patterns available in this filter.
   */
  public FilterDialog(Component parent,
                      TodoFilter filter,
                      int filterIndex,
                      List<TodoFilter> filters,
                      List<TodoPattern> patterns) {
    super(parent, true);
    myFilter = filter;
    myFilterIndex = filterIndex;
    myPatterns = patterns;
    myFilters = filters;
    myNameField = new JTextField(filter.getName());
    MyModel model = new MyModel();
    myTable = new Table(model);
    init();
  }

  protected void doOKAction() {

    // Validate filter name

    myFilter.setName(myNameField.getText().trim());
    if (myFilter.getName().length() == 0) {
      Messages.showMessageDialog(myTable,
                                 IdeBundle.message("error.filter.name.should.be.specified"),
                                 CommonBundle.getErrorTitle(),
                                 Messages.getErrorIcon());
      return;
    }
    for (int i = 0; i < myFilters.size(); i++) {
      TodoFilter filter = myFilters.get(i);
      if (myFilterIndex != i && myFilter.getName().equals(filter.getName())) {
        Messages.showMessageDialog(myTable,
                                   IdeBundle.message("error.filter.with.the.same.name.already.exists"),
                                   CommonBundle.getErrorTitle(),
                                   Messages.getErrorIcon());
        return;
      }
    }

    // Validate that at least one pettern is selected

    if (myFilter.isEmpty()) {
      Messages.showMessageDialog(myTable,
                                 IdeBundle.message("error.filter.should.contain.at.least.one.pattern"),
                                 CommonBundle.getErrorTitle(),
                                 Messages.getErrorIcon());
      return;
    }

    super.doOKAction();
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp("reference.idesettings.todo.editfilter");
  }

  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    JLabel nameLabel = new JLabel(IdeBundle.message("label.todo.filter.name"));
    panel.add(nameLabel,
              new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 5, 10), 0, 0));
    panel.add(myNameField,
              new GridBagConstraints(1, 0, 1, 1, 1, 0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 5, 0), 0, 0));

    JPanel patternsPanel = new JPanel(new GridBagLayout());
    Border border = IdeBorderFactory.createTitledBorder(IdeBundle.message("group.todo.filter.patterns"));
    patternsPanel.setBorder(border);
    myTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    JBScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTable);
    scrollPane.setPreferredSize(new Dimension(550, myTable.getRowHeight() * 10));
    patternsPanel.add(scrollPane,
                      new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));

    // Column "Available"

    int width = new JCheckBox().getPreferredSize().width;
    TableColumn availableColumn = myTable.getColumnModel().getColumn(0);
    availableColumn.setPreferredWidth(width);
    availableColumn.setMaxWidth(width);
    availableColumn.setMinWidth(width);

    //

    panel.add(patternsPanel,
              new GridBagConstraints(0, 1, 2, 1, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));

    return panel;
  }

  private final class MyModel extends AbstractTableModel {
    private final String[] ourColumnNames = new String[]{" ", IdeBundle.message("column.todo.filter.pattern"), };
    private final Class[] ourColumnClasses = new Class[]{Boolean.class, String.class};

    public String getColumnName(int column) {
      return ourColumnNames[column];
    }

    public Class getColumnClass(int column) {
      return ourColumnClasses[column];
    }

    public int getColumnCount() {
      return 2;
    }

    public int getRowCount() {
      return myPatterns.size();
    }

    public Object getValueAt(int row, int column) {
      TodoPattern pattern = myPatterns.get(row);
      switch (column) {
        case 0:
          // "Available" column
          return myFilter.contains(pattern) ? Boolean.TRUE : Boolean.FALSE;
        case 1:
          // "Pattern" column
          return pattern.getPatternString();
        default:
          throw new IllegalArgumentException();
      }
    }

    public void setValueAt(Object value, int row, int column) {
      switch (column) {
        case 0:
          TodoPattern pattern = myPatterns.get(row);
          if (((Boolean)value).booleanValue()) {
            myFilter.addTodoPattern(pattern);
          }
          else {
            myFilter.removeTodoPattern(pattern);
          }
          break;
        default:
          throw new IllegalArgumentException();
      }
    }

    public boolean isCellEditable(int row, int column) {
      return column == 0;
    }
  }
}

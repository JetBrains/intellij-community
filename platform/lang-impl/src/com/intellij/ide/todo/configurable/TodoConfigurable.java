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

import com.intellij.ide.IdeBundle;
import com.intellij.ide.todo.TodoConfiguration;
import com.intellij.ide.todo.TodoFilter;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.search.TodoAttributes;
import com.intellij.psi.search.TodoPattern;
import com.intellij.ui.PanelWithButtons;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TableUtil;
import com.intellij.util.ui.Table;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Vladimir Kondratyev
 */
public class TodoConfigurable extends BaseConfigurable implements SearchableConfigurable {
  /*
   * UI resources
   */
  private JPanel myPanel;
  private JTable myPatternsTable;
  private JTable myFiltersTable;
  private JButton myAddPatternButton;
  private JButton myEditPatternButton;
  private JButton myRemovePatternButton;
  private JButton myAddFilterButton;
  private JButton myEditFilterButton;
  private JButton myRemoveFilterButton;
  private final List<TodoPattern> myPatterns;
  private final PatternsTableModel myPatternsModel;
  private final List<TodoFilter> myFilters;
  private final FiltersTableModel myFiltersModel;

  /**
   * Invoked by reflection
   */
  public TodoConfigurable() {
    myPatterns = new ArrayList<TodoPattern>();
    myFilters = new ArrayList<TodoFilter>();
    myFiltersModel = new FiltersTableModel(myFilters);
    myPatternsModel = new PatternsTableModel(myPatterns);
  }

  private boolean arePatternsModified() {
    TodoConfiguration todoConfiguration = TodoConfiguration.getInstance();
    TodoPattern[] initialPatterns = todoConfiguration.getTodoPatterns();
    if (initialPatterns.length != myPatterns.size()) {
      return true;
    }
    for (int i = 0; i < initialPatterns.length; i++) {
      TodoPattern initialPattern = initialPatterns[i];
      if (!myPatterns.contains(initialPattern)) {
        return true;
      }
    }
    return false;
  }

  private boolean areFiltersModified() {
    TodoConfiguration todoConfiguration = TodoConfiguration.getInstance();
    TodoFilter[] initialFilters = todoConfiguration.getTodoFilters();
    if (initialFilters.length != myFilters.size()) {
      return true;
    }
    for (int i = 0; i < initialFilters.length; i++) {
      TodoFilter initialFilter = initialFilters[i];
      if (!myFilters.contains(initialFilter)) {
        return true;
      }
    }
    return false;
  }

  public boolean isModified() {
    // This method is always invoked before close configuration dialog or leave "ToDo" page.
    // So it's a good place to commit all changes.
    stopEditing();
    return arePatternsModified() || areFiltersModified();
  }

  public void apply() throws ConfigurationException {
    stopEditing();
    if (arePatternsModified()) {
      TodoPattern[] patterns = myPatterns.toArray(new TodoPattern[myPatterns.size()]);
      TodoConfiguration.getInstance().setTodoPatterns(patterns);
    }
    if (areFiltersModified()) {
      TodoFilter[] filters = myFilters.toArray(new TodoFilter[myFilters.size()]);
      TodoConfiguration.getInstance().setTodoFilters(filters);
    }
  }

  public void disposeUIResources() {
    myPanel = null;
    myPatternsModel.removeTableModelListener(myPatternsTable);
    myPatternsTable = null;

    myAddPatternButton = null;
    myEditPatternButton = null;
    myRemovePatternButton = null;

    myFiltersModel.removeTableModelListener(myFiltersTable);
    myFiltersTable = null;

    myAddFilterButton = null;
    myEditFilterButton = null;
    myRemoveFilterButton = null;
  }

  public JComponent createComponent() {
    // Panel with patterns

    PanelWithButtons patternsPanel = new PanelWithButtons() {
      {
        initPanel();
      }

      protected String getLabelText() {
        return IdeBundle.message("label.todo.patterns");
      }

      protected JComponent createMainComponent() {
        // JTable with TodoPaterns

        myPatternsTable = new Table(myPatternsModel);
        myPatternsTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Column "Icon"

        JComboBox todoTypeCombo =
          new JComboBox(new Icon[]{TodoAttributes.DEFAULT_ICON, TodoAttributes.QUESTION_ICON, TodoAttributes.IMPORTANT_ICON});
        todoTypeCombo.setRenderer(new TodoTypeListCellRenderer());
        TableColumn typeColumn = myPatternsTable.getColumnModel().getColumn(0);
        DefaultCellEditor todoTypeEditor = new DefaultCellEditor(todoTypeCombo);
        todoTypeEditor.setClickCountToStart(1);
        typeColumn.setCellEditor(todoTypeEditor);
        TodoTypeTableCellRenderer todoTypeRenderer = new TodoTypeTableCellRenderer();
        typeColumn.setCellRenderer(todoTypeRenderer);

        int width = myPatternsTable.getFontMetrics(myPatternsTable.getFont()).stringWidth(myPatternsTable.getColumnName(0)) + 10;
        typeColumn.setPreferredWidth(width);
        typeColumn.setMaxWidth(width);
        typeColumn.setMinWidth(width);

        // Column "Case Sensitive"

        TableColumn todoCaseSensitiveColumn = myPatternsTable.getColumnModel().getColumn(1);
        width = myPatternsTable.getFontMetrics(myPatternsTable.getFont()).stringWidth(myPatternsTable.getColumnName(1)) + 10;
        todoCaseSensitiveColumn.setPreferredWidth(width);
        todoCaseSensitiveColumn.setMaxWidth(width);
        todoCaseSensitiveColumn.setMinWidth(width);

        // Column "Pattern"

        TodoPatternTableCellRenderer todoPatternRenderer = new TodoPatternTableCellRenderer(myPatterns);
        TableColumn patternColumn = myPatternsTable.getColumnModel().getColumn(2);
        patternColumn.setCellRenderer(todoPatternRenderer);


        ((DefaultCellEditor)myPatternsTable.getDefaultEditor(String.class)).setClickCountToStart(2);
        JScrollPane myPatternsScroll = ScrollPaneFactory.createScrollPane(myPatternsTable);
//        myPatternsScroll.getViewport().add(myPatternsTable);
        myPatternsScroll.setPreferredSize(new Dimension(-1, myPatternsTable.getRowHeight() * 12));

        return myPatternsScroll;
      }

      protected JButton[] createButtons() {
        myAddPatternButton = new JButton(IdeBundle.message("button.add"));
        myAddPatternButton.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            stopEditing();
            TodoPattern pattern = new TodoPattern();
            PatternDialog dialog = new PatternDialog(myPanel, pattern);
            dialog.setTitle(IdeBundle.message("title.add.todo.pattern"));
            dialog.show();
            if (!dialog.isOK()) {
              return;
            }
            myPatterns.add(pattern);
            int index = myPatterns.size() - 1;
            myPatternsModel.fireTableRowsInserted(index, index);
            myPatternsTable.getSelectionModel().setSelectionInterval(index, index);
            myPatternsTable.scrollRectToVisible(myPatternsTable.getCellRect(index, 0, true));
          }
        });

        myEditPatternButton = new JButton(IdeBundle.message("button.edit"));
        myEditPatternButton.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            editSelectedPattern();
          }
        });

        myRemovePatternButton = new JButton(IdeBundle.message("button.remove"));
        myRemovePatternButton.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            stopEditing();
            int selectedIndex = myPatternsTable.getSelectedRow();
            if (selectedIndex < 0 || selectedIndex >= myPatternsModel.getRowCount()) {
              return;
            }
            TodoPattern patternToBeRemoved = myPatterns.get(selectedIndex);
            TableUtil.removeSelectedItems(myPatternsTable);
            for (int i = 0; i < myFilters.size(); i++) {
              TodoFilter filter = myFilters.get(i);
              if (filter.contains(patternToBeRemoved)) {
                filter.removeTodoPattern(patternToBeRemoved);
                myFiltersModel.fireTableRowsUpdated(i, i);
              }
            }
          }
        });

        return new JButton[]{myAddPatternButton, myEditPatternButton, myRemovePatternButton};
      }
    };

    // double click in "Patterns" table should also start editing of selected pattern

    myPatternsTable.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
          editSelectedPattern();
        }
      }
    });

    // synchonizes selection in the "Patterns" table with buttons states

    myPatternsTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
          return;
        }
        updateButtonsState();
      }
    });

    // Panel with filters

    PanelWithButtons filtersPanel = new PanelWithButtons() {
      {
        initPanel();
      }

      protected String getLabelText() {
        return IdeBundle.message("label.todo.filters");
      }

      protected JComponent createMainComponent() {
        myFiltersTable = new Table(myFiltersModel);
        myFiltersTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane myFiltersScroll = ScrollPaneFactory.createScrollPane(myFiltersTable);
//        myFiltersScroll.getViewport().add(myFiltersTable);
        myFiltersScroll.setPreferredSize(new Dimension(-1, myPatternsTable.getRowHeight() * 12));

        // Column "Name"

        TableColumn nameColumn = myFiltersTable.getColumnModel().getColumn(0);
        int width = myPatternsTable.getColumnModel().getColumn(0).getPreferredWidth()/*typeColumn*/ +
                    myPatternsTable.getColumnModel().getColumn(1).getPreferredWidth()/*todoCaseSensitiveColumn*/;
        nameColumn.setPreferredWidth(width);
        nameColumn.setMaxWidth(width);
        nameColumn.setMinWidth(width);
        nameColumn.setCellRenderer(new MyFilterNameTableCellRenderer());

        return myFiltersScroll;
      }

      protected JButton[] createButtons() {
        myAddFilterButton = new JButton(IdeBundle.message("button.add.d"));
        myAddFilterButton.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            stopEditing();
            TodoFilter filter = new TodoFilter();
            FilterDialog dialog = new FilterDialog(myPanel, filter, -1, myFilters, myPatterns);
            dialog.setTitle(IdeBundle.message("title.add.todo.filter"));
            dialog.show();
            int exitCode = dialog.getExitCode();
            if (DialogWrapper.OK_EXIT_CODE == exitCode) {
              myFilters.add(filter);
              int index = myFilters.size() - 1;
              myFiltersModel.fireTableRowsInserted(index, index);
              myFiltersTable.getSelectionModel().setSelectionInterval(index, index);
              myFiltersTable.scrollRectToVisible(myFiltersTable.getCellRect(index, 0, true));
            }
          }
        });

        myEditFilterButton = new JButton(IdeBundle.message("button.edit.t"));
        myEditFilterButton.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            editSelectedFilter();
          }
        });

        myRemoveFilterButton = new JButton(IdeBundle.message("button.remove.m"));
        myRemoveFilterButton.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            stopEditing();
            TableUtil.removeSelectedItems(myFiltersTable);
          }
        });

        return new JButton[]{myAddFilterButton, myEditFilterButton, myRemoveFilterButton};
      }
    };

    // double click in "Filters" table should also start editing of selected filter

    myFiltersTable.addMouseListener(
      new MouseAdapter() {
        public void mouseClicked(MouseEvent e) {
          if (e.getClickCount() == 2) {
            editSelectedFilter();
          }
        }
      }
    );

    // synchonizes selection in the "Filters" table with buttons states

    myFiltersTable.getSelectionModel().addListSelectionListener(
      new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent e) {
          if (e.getValueIsAdjusting()) {
            return;
          }
          updateButtonsState();
        }
      }
    );

    //
    myPanel = new JPanel(new GridBagLayout());
    myPanel.add(patternsPanel, new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL,
                                                      new Insets(5, 2, 4, 2), 0, 0));
    myPanel.add(filtersPanel, new GridBagConstraints(0, 1, 1, 1, 1, 1, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL,
                                                     new Insets(5, 2, 4, 2), 0, 0));

    myPanel.setPreferredSize(new Dimension(Math.max(700, myPanel.getPreferredSize().width), myPanel.getPreferredSize().height));
    return myPanel;
  }

  private void editSelectedPattern() {
    stopEditing();
    int selectedIndex = myPatternsTable.getSelectedRow();
    if (selectedIndex < 0 || selectedIndex >= myPatternsModel.getRowCount()) {
      return;
    }
    TodoPattern sourcePattern = myPatterns.get(selectedIndex);
    TodoPattern pattern = sourcePattern.clone();
    PatternDialog dialog = new PatternDialog(TodoConfigurable.this.myPanel, pattern);
    dialog.setTitle(IdeBundle.message("title.edit.todo.pattern"));
    dialog.show();
    if (!dialog.isOK()) {
      return;
    }
    myPatterns.set(selectedIndex, pattern);
    myPatternsModel.fireTableRowsUpdated(selectedIndex, selectedIndex);
    myPatternsTable.getSelectionModel().setSelectionInterval(selectedIndex, selectedIndex);
    // Update model with patterns
    for (int i = 0; i < myFilters.size(); i++) {
      TodoFilter filter = myFilters.get(i);
      if (filter.contains(sourcePattern)) {
        filter.removeTodoPattern(sourcePattern);
        filter.addTodoPattern(pattern);
        myFiltersModel.fireTableRowsUpdated(i, i);
      }
    }
  }

  private void editSelectedFilter() {
    stopEditing();
    int selectedIndex = myFiltersTable.getSelectedRow();
    if (selectedIndex < 0 || selectedIndex >= myFiltersModel.getRowCount()) {
      return;
    }
    TodoFilter sourceFilter = myFilters.get(selectedIndex);
    TodoFilter filter = sourceFilter.clone();
    FilterDialog dialog = new FilterDialog(myPanel, filter, selectedIndex, myFilters, myPatterns);
    dialog.setTitle(IdeBundle.message("title.edit.todo.filter"));
    dialog.show();
    int exitCode = dialog.getExitCode();
    if (DialogWrapper.OK_EXIT_CODE == exitCode) {
      myFilters.set(selectedIndex, filter);
      myFiltersModel.fireTableRowsUpdated(selectedIndex, selectedIndex);
      myFiltersTable.getSelectionModel().setSelectionInterval(selectedIndex, selectedIndex);
    }
  }

  private void stopEditing() {
    if (myPatternsTable.isEditing()) {
      TableCellEditor editor = myPatternsTable.getCellEditor();
      if (editor != null) {
        editor.stopCellEditing();
      }
    }
    if (myFiltersTable.isEditing()) {
      TableCellEditor editor = myFiltersTable.getCellEditor();
      if (editor != null) {
        editor.stopCellEditing();
      }
    }
  }

  private void updateButtonsState() {
    int selectedPatternIndex = myPatternsTable.getSelectedRow();
    myEditPatternButton.setEnabled(selectedPatternIndex != -1);
    myRemovePatternButton.setEnabled(selectedPatternIndex != -1);

    int selectedFilterIndex = myFiltersTable.getSelectedRow();
    myEditFilterButton.setEnabled(selectedFilterIndex != -1);
    myRemoveFilterButton.setEnabled(selectedFilterIndex != -1);
  }

  public String getDisplayName() {
    return IdeBundle.message("title.todo");
  }

  public String getHelpTopic() {
    return "preferences.toDoOptions";
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/configurableTodo.png");
  }

  public void reset() {
    // Patterns
    myPatterns.clear();
    TodoConfiguration todoConfiguration = TodoConfiguration.getInstance();
    TodoPattern[] patterns = todoConfiguration.getTodoPatterns();
    for (int i = 0; i < patterns.length; i++) {
      myPatterns.add(patterns[i].clone());
    }
    myPatternsModel.fireTableDataChanged();
    // Filters
    myFilters.clear();
    TodoFilter[] filters = todoConfiguration.getTodoFilters();
    for (int i = 0; i < filters.length; i++) {
      myFilters.add(filters[i].clone());
    }
    myFiltersModel.fireTableDataChanged();
    //
    updateButtonsState();
  }

  public static TodoConfigurable getInstance() {
    return ShowSettingsUtil.getInstance().findApplicationConfigurable(TodoConfigurable.class);
  }

  private final class MyFilterNameTableCellRenderer extends DefaultTableCellRenderer {
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      TodoFilter filter = myFilters.get(row);
      if (isSelected) {
        setForeground(UIUtil.getTableSelectionForeground());
      }
      else {
        if (filter.isEmpty()) {
          setForeground(Color.RED);
        }
        else {
          setForeground(UIUtil.getTableForeground());
        }
      }
      return this;
    }
  }

  public String getId() {
    return getHelpTopic();
  }

  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }
}

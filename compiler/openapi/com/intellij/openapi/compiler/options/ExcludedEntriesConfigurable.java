/*
 * Copyright 2000-2006 JetBrains s.r.o.
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

package com.intellij.openapi.compiler.options;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.PanelWithButtons;
import com.intellij.ui.RightAlignedLabelUI;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ui.Table;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

public class ExcludedEntriesConfigurable implements UnnamedConfigurable {
  private Project myProject;
  private ArrayList<ExcludeEntryDescription> myExcludeEntryDescriptions = new ArrayList<ExcludeEntryDescription>();
  private ExcludedEntriesConfiguration myConfiguration;

  public ExcludedEntriesConfigurable(Project project, final ExcludedEntriesConfiguration configuration) {
    myConfiguration = configuration;
    myProject = project;
  }

  public void reset() {
    ExcludeEntryDescription[] descriptions = myConfiguration.getExcludeEntryDescriptions();
    myExcludeEntryDescriptions.clear();
    for (ExcludeEntryDescription description : descriptions) {
      myExcludeEntryDescriptions.add(description.copy());
    }
  }

  public void apply() {
    myConfiguration.removeAllExcludeEntryDescriptions();
    for (ExcludeEntryDescription description : myExcludeEntryDescriptions) {
      myConfiguration.addExcludeEntryDescription(description);
    }
  }

  public boolean isModified() {
    ExcludeEntryDescription[] excludeEntryDescriptions = myConfiguration.getExcludeEntryDescriptions();
    if(excludeEntryDescriptions.length != myExcludeEntryDescriptions.size()) {
      return true;
    }
    for(int i = 0; i < excludeEntryDescriptions.length; i++) {
      ExcludeEntryDescription description = excludeEntryDescriptions[i];
      if(!Comparing.equal(description, myExcludeEntryDescriptions.get(i))) {
        return true;
      }
    }
    return false;
  }

  public JComponent createComponent() {
    return new ExcludedEntriesPanel();
  }

  public void disposeUIResources() {
  }

  private class ExcludedEntriesPanel extends PanelWithButtons {
    private JButton myRemoveButton;
    private Table myExcludedTable;

    public ExcludedEntriesPanel() {
      initPanel();
    }

    protected String getLabelText(){
      return null;
    }

    protected JButton[] createButtons(){
      final JButton addButton = new JButton(IdeBundle.message("button.add"));
      addButton.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent e){
            addPath(myConfiguration.getFileChooserDescriptor());
          }
        }
      );

      myRemoveButton = new JButton(IdeBundle.message("button.remove"));
      myRemoveButton.addActionListener(
        new ActionListener(){
          public void actionPerformed(ActionEvent e){
            removePath();
          }
        }
      );
      myRemoveButton.setEnabled(false);
      myRemoveButton.getModel().addChangeListener(new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
          if (myExcludedTable.getSelectedRow() == -1) {
            myRemoveButton.setEnabled(false);
          }
        }
      });

      return new JButton[]{addButton, myRemoveButton};
    }

    private void addPath(FileChooserDescriptor descriptor) {
      int selected = myExcludedTable.getSelectedRow() + 1;
      if(selected < 0) {
        selected = myExcludeEntryDescriptions.size();
      }
      int savedSelected = selected;
      VirtualFile[] chosen = FileChooser.chooseFiles(myProject, descriptor);
      for (final VirtualFile chosenFile : chosen) {
        ExcludeEntryDescription description;
        if (isFileExcluded(chosenFile)) {
          continue;
        }
        if (chosenFile.isDirectory()) {
          description = new ExcludeEntryDescription(chosenFile, true, false);
        }
        else {
          description = new ExcludeEntryDescription(chosenFile, false, true);
        }
        myExcludeEntryDescriptions.add(selected, description);
        selected++;
      }
      if (selected > savedSelected) { // actually added something
        AbstractTableModel model = (AbstractTableModel)myExcludedTable.getModel();
        model.fireTableRowsInserted(savedSelected, selected-1);
        myExcludedTable.setRowSelectionInterval(savedSelected, selected-1);
      }
    }

    private boolean isFileExcluded(VirtualFile file) {
      for (final ExcludeEntryDescription description : myExcludeEntryDescriptions) {
        final VirtualFile descriptionFile = description.getVirtualFile();
        if (descriptionFile == null) {
          continue;
        }
        if (file.equals(descriptionFile)) {
          return true;
        }
      }
      return false;
    }

    private void removePath() {
      int selected = myExcludedTable.getSelectedRow();
      if(selected < 0)
        return;
      if(myExcludedTable.isEditing()) {
        TableCellEditor editor = myExcludedTable.getCellEditor();
        if (editor != null) {
          editor.stopCellEditing();
        }
      }
      myExcludeEntryDescriptions.remove(selected);
      AbstractTableModel model = (AbstractTableModel)myExcludedTable.getModel();
      model.fireTableRowsDeleted(selected, selected);
      if(selected >= myExcludeEntryDescriptions.size()) {
        selected --;
      }
      if(selected >= 0) {
        myExcludedTable.setRowSelectionInterval(selected, selected);
      }
    }

    protected JComponent createMainComponent(){
      final String[] names = {
        CompilerBundle.message("exclude.from.compile.table.path.column.name"),
        CompilerBundle.message("exclude.from.compile.table.recursively.column.name")
      };
      // Create a model of the data.
      TableModel dataModel = new AbstractTableModel() {
        public int getColumnCount() {
          return names.length;
        }

        public int getRowCount() {
          return myExcludeEntryDescriptions.size();
        }

        public Object getValueAt(int row, int col) {
          ExcludeEntryDescription description = myExcludeEntryDescriptions.get(row);
          if(col == 0) {
            return description;
          }
          if(col == 1) {
            if(!description.isFile()) {
              return description.isIncludeSubdirectories() ? Boolean.TRUE : Boolean.FALSE;
            }
            else {
              return null;
            }
          }
          return null;
        }

        public String getColumnName(int column) {
          return names[column];
        }

        public Class getColumnClass(int c) {
          if(c == 0) {
            return Object.class;
          }
          if(c == 1) {
            return Boolean.class;
          }
          return null;
        }

        public boolean isCellEditable(int row, int col) {
          if(col == 1) {
            ExcludeEntryDescription description = myExcludeEntryDescriptions.get(row);
            return !description.isFile();
          }
          return false;
        }

        public void setValueAt(Object aValue, int row, int col) {
          ExcludeEntryDescription description = myExcludeEntryDescriptions.get(row);
          description.setIncludeSubdirectories(aValue.equals(Boolean.TRUE));
        }
      };

      myExcludedTable = new Table(dataModel);
      myExcludedTable.setPreferredScrollableViewportSize(new Dimension(300, myExcludedTable.getRowHeight() * 6));
      myExcludedTable.setDefaultRenderer(Boolean.class, new BooleanRenderer());
      myExcludedTable.setDefaultRenderer(Object.class, new MyObjectRenderer());
      myExcludedTable.getColumn(names[0]).setPreferredWidth(350);
      myExcludedTable.getColumn(names[1]).setPreferredWidth(140);
      myExcludedTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      myExcludedTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent e) {
          myRemoveButton.setEnabled(myExcludedTable.getSelectedRow() >= 0);
        }
      });
      TableCellEditor editor = myExcludedTable.getDefaultEditor(String.class);
      if(editor instanceof DefaultCellEditor) {
        ((DefaultCellEditor)editor).setClickCountToStart(1);
      }

      return ScrollPaneFactory.createScrollPane(myExcludedTable);
    }
  }

  private static class BooleanRenderer extends JCheckBox implements TableCellRenderer {
    private JPanel myPanel = new JPanel();

    public BooleanRenderer() {
      setHorizontalAlignment(JLabel.CENTER);
    }

    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus,
                                                   int row, int column) {
      if(value == null) {
        if(isSelected) {
          myPanel.setBackground(table.getSelectionBackground());
        }
        else {
          myPanel.setBackground(table.getBackground());
        }
        return myPanel;
      }
      if(isSelected) {
        setForeground(table.getSelectionForeground());
        super.setBackground(table.getSelectionBackground());
      }
      else {
        setForeground(table.getForeground());
        setBackground(table.getBackground());
      }
      setSelected(((Boolean)value).booleanValue());
      return this;
    }
  }

  private static class MyObjectRenderer extends DefaultTableCellRenderer {
    public MyObjectRenderer() {
      setUI(new RightAlignedLabelUI());
    }

    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      if (value instanceof ExcludeEntryDescription) {
        ExcludeEntryDescription description = (ExcludeEntryDescription)value;
        setText(description.getPresentableUrl());
        if(!description.isValid()){
          setForeground(Color.RED);
        }
      }

      if (!isSelected) {
        setBackground(table.getBackground());
      }
      return component;
    }
  }

}
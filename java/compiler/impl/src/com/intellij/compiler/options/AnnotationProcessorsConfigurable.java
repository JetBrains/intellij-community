/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.compiler.options;

import com.intellij.compiler.CompileServerManager;
import com.intellij.compiler.CompilerConfiguration;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.EditableModel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: Oct 5, 2009
 */
public class AnnotationProcessorsConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private ProcessedModulesTable myModulesTable;
  private final Project myProject;
  private JRadioButton myRbClasspath;
  private JRadioButton myRbProcessorsPath;
  private TextFieldWithBrowseButton myProcessorPathField;
  private ProcessorTableModel myProcessorsModel;
  private JCheckBox myCbEnableProcessing;
  private JBTable myProcessorTable;
  private JPanel myProcessorPanel;

  public AnnotationProcessorsConfigurable(final Project project) {
    myProject = project;
  }

  public String getDisplayName() {
    return "Annotation Processors";
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return "reference.projectsettings.compiler.annotationProcessors";
  }

  @NotNull
  public String getId() {
    return getHelpTopic();
  }

  public Runnable enableSearch(String option) {
    return null;
  }

  public JComponent createComponent() {
    final JPanel mainPanel = new JPanel(new GridBagLayout());

    myCbEnableProcessing = new JCheckBox("Enable annotation processing");

    myRbClasspath = new JRadioButton("Obtain processors from project classpath");
    myRbProcessorsPath = new JRadioButton("Processor path:");
    ButtonGroup group = new ButtonGroup();
    group.add(myRbClasspath);
    group.add(myRbProcessorsPath);

    myProcessorPathField = new TextFieldWithBrowseButton(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final VirtualFile[] files =
          FileChooser.chooseFiles(myProcessorPathField, FileChooserDescriptorFactory.createAllButJarContentsDescriptor());
        if (files.length > 0) {
          final StringBuilder builder = new StringBuilder();
          for (VirtualFile file : files) {
            if (builder.length() > 0) {
              builder.append(File.pathSeparator);
            }
            builder.append(FileUtil.toSystemDependentName(file.getPath()));
          }
          myProcessorPathField.setText(builder.toString());
        }
      }
    });

    final JPanel processorTablePanel = new JPanel(new BorderLayout());
    myProcessorsModel = new ProcessorTableModel();
    processorTablePanel.setBorder(IdeBorderFactory.createTitledBorder("Annotation Processors", false, false, true));
    myProcessorTable = new JBTable(myProcessorsModel);
    myProcessorTable.getEmptyText().setText("No processors configured");
    myProcessorPanel = ToolbarDecorator.createDecorator(myProcessorTable)
      .disableUpAction()
      .disableDownAction()
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton anActionButton) {
          final TableCellEditor cellEditor = myProcessorTable.getCellEditor();
          if (cellEditor != null) {
            cellEditor.stopCellEditing();
          }
          final ProcessorTableModel model = (ProcessorTableModel)myProcessorTable.getModel();
          model.addRow();
          TableUtil.editCellAt(myProcessorTable, model.getRowCount() - 1, ProcessorTableRow.NAME_COLUMN);
        }
      })
      .createPanel();



    processorTablePanel.add(myProcessorPanel, BorderLayout.CENTER);

    myModulesTable = new ProcessedModulesTable(myProject);
    myModulesTable.setBorder(IdeBorderFactory.createTitledBorder("Processed Modules", false, false, true));
    final JLabel noteMessage = new JLabel("<html>Source files generated by annotation processors will be stored under the project output directory. " +
                                                  "To override this behaviour for certain modules you may specify the directory name in the table below. " +
                                                  "If specified, the directory will be created under corresponding module's content root.</html>");
    
    final JLabel warning = new JLabel("<html>WARNING!<br>" +
                                              "All source files located in the generated sources output directory WILL BE EXCLUDED from annotation processing. " +
                                              "If option 'Clear output directory on rebuild' is enabled, " +
                                              "the entire contents of directories specified in the table below WILL BE CLEARED on rebuild.</html>");
    warning.setFont(warning.getFont().deriveFont(Font.BOLD));

    mainPanel.add(myCbEnableProcessing, new GridBagConstraints(0, 0, 2, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
    mainPanel.add(myRbClasspath, new GridBagConstraints(0, 1, 2, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(10, 0, 0, 0), 0, 0));
    mainPanel.add(myRbProcessorsPath, new GridBagConstraints(0, 2, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(5, 0, 0, 0), 0, 0));
    mainPanel.add(myProcessorPathField, new GridBagConstraints(1, 2, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(5, 5, 0, 0), 0, 0));
    mainPanel.add(processorTablePanel, new GridBagConstraints(0, 3, 2, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(10, 0, 0, 0), 0, 0));
    mainPanel.add(noteMessage, new GridBagConstraints(0, 4, 2, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(10, 5, 0, 0), 0, 0));
    mainPanel.add(warning, new GridBagConstraints(0, 5, 2, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(10, 5, 0, 0), 0, 0));
    mainPanel.add(myModulesTable, new GridBagConstraints(0, 6, 2, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(10, 0, 0, 0), 0, 0));


    myRbClasspath.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        updateEnabledState();
      }
    });

    myProcessorTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        if (!e.getValueIsAdjusting()) {
          updateEnabledState();
        }
      }
    });

    myCbEnableProcessing.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        updateEnabledState();
      }
    });

    updateEnabledState();
    
    return mainPanel;
  }

  private void updateEnabledState() {
    final boolean enabled = myCbEnableProcessing.isSelected();
    final boolean useProcessorpath = !myRbClasspath.isSelected();
    myRbClasspath.setEnabled(enabled);
    myRbProcessorsPath.setEnabled(enabled);
    myProcessorPathField.setEnabled(enabled && useProcessorpath);
    final AnActionButton addButton = ToolbarDecorator.findAddButton(myProcessorPanel);
    if (addButton != null) {
      addButton.setEnabled(enabled);
    }
    final AnActionButton removeButton = ToolbarDecorator.findRemoveButton(myProcessorPanel);
    if (removeButton != null) {
      removeButton.setEnabled(enabled && myProcessorTable.getSelectedRow() >= 0);
    }
    myProcessorTable.setEnabled(enabled);
    final JTableHeader header = myProcessorTable.getTableHeader();
    if (header != null) {
      header.repaint();
    }
    myModulesTable.getComponent().setEnabled(enabled);
  }

  public boolean isModified() {
    final CompilerConfiguration config = CompilerConfiguration.getInstance(myProject);
    if (config.isAnnotationProcessorsEnabled() != myCbEnableProcessing.isSelected()) {
      return true;
    }
    if (config.isObtainProcessorsFromClasspath() != myRbClasspath.isSelected()) {
      return true;
    }
    if (!FileUtil.pathsEqual(config.getProcessorPath(), FileUtil.toSystemIndependentName(myProcessorPathField.getText().trim()))) {
      return true;
    }

    final Map<String, String> map = myProcessorsModel.exportToMap();
    if (!map.equals(config.getAnnotationProcessorsMap())) {
      return true;
    }

    if (!getMarkedModules().equals(config.getAnotationProcessedModules())) {
      return true;
    }

    return false;
  }

  public void apply() throws ConfigurationException {
    final CompilerConfiguration config = CompilerConfiguration.getInstance(myProject);
    config.setAnnotationProcessorsEnabled(myCbEnableProcessing.isSelected());

    config.setObtainProcessorsFromClasspath(myRbClasspath.isSelected());
    config.setProcessorsPath(FileUtil.toSystemIndependentName(myProcessorPathField.getText().trim()));

    config.setAnnotationProcessorsMap(myProcessorsModel.exportToMap());

    config.setAnotationProcessedModules(getMarkedModules());
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        CompileServerManager.getInstance().sendReloadRequest(myProject);
      }
    });
  }

  private Map<Module, String> getMarkedModules() {
    final Map<Module, String> result = new HashMap<Module, String>();
    for (Pair<Module, String> pair : myModulesTable.getAllModules()) {
      result.put(pair.getFirst(), pair.getSecond());
    }
    return result;
  }

  public void reset() {
    final CompilerConfiguration config = CompilerConfiguration.getInstance(myProject);
    myCbEnableProcessing.setSelected(config.isAnnotationProcessorsEnabled());

    final boolean obtainFromClasspath = config.isObtainProcessorsFromClasspath();
    if (obtainFromClasspath) {
      myRbClasspath.setSelected(true);
    }
    else {
      myRbProcessorsPath.setSelected(true);
    }

    myProcessorPathField.setText(FileUtil.toSystemDependentName(config.getProcessorPath()));

    myProcessorsModel.setProcessorMap(config.getAnnotationProcessorsMap());
    
    myModulesTable.removeAllElements();
    for (final Module module : ModuleManager.getInstance(myProject).getModules()) {
      if (config.isAnnotationProcessingEnabled(module)) {
        myModulesTable.addModule(module, config.getGeneratedSourceDirName(module));
      }
    }
    myModulesTable.sort(new Comparator<Module>() {
      public int compare(Module o1, Module o2) {
        return o1.getName().compareToIgnoreCase(o2.getName());
      }
    });
  }

  public void disposeUIResources() {
  }

  private static class ProcessorTableModel extends AbstractTableModel implements EditableModel {
    private final java.util.List<ProcessorTableRow> myRows = new ArrayList<ProcessorTableRow>();

    public String getColumnName(int column) {
      switch (column) {
        case ProcessorTableRow.NAME_COLUMN: return "Processor FQ Name";
        case ProcessorTableRow.OPTIONS_COLUMN : return "Processor Run Options (space-separated \"key=value\" pairs)";
      }
      return super.getColumnName(column);
    }

    public Class<?> getColumnClass(int columnIndex) {
      return String.class;
    }

    public int getRowCount() {
      return myRows.size();
    }

    public int getColumnCount() {
      return 2;
    }

    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return columnIndex == ProcessorTableRow.NAME_COLUMN || columnIndex == ProcessorTableRow.OPTIONS_COLUMN;
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
      final ProcessorTableRow row = myRows.get(rowIndex);
      switch (columnIndex) {
        case ProcessorTableRow.NAME_COLUMN: return row.name;
        case ProcessorTableRow.OPTIONS_COLUMN : return row.options;
      }
      return null;
    }

    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      if (aValue != null) {
        final ProcessorTableRow row = myRows.get(rowIndex);
        switch (columnIndex) {
          case ProcessorTableRow.NAME_COLUMN:
            row.name = (String)aValue;
            break;
          case ProcessorTableRow.OPTIONS_COLUMN:
            row.options = (String)aValue;
            break;
        }
      }
    }

    public void removeRow(int idx) {
      myRows.remove(idx);
      fireTableRowsDeleted(idx, idx);
    }

    @Override
    public void exchangeRows(int oldIndex, int newIndex) {
    }

    public void addRow() {
      myRows.add(new ProcessorTableRow());
      final int index = myRows.size() - 1;
      fireTableRowsInserted(index, index);
    }

    public void setProcessorMap(Map<String, String> processorMap) {
      clear();
      if (processorMap.size() > 0) {
        for (Map.Entry<String, String> entry : processorMap.entrySet()) {
          myRows.add(new ProcessorTableRow(entry.getKey(), entry.getValue()));
        }
        Collections.sort(myRows, new Comparator<ProcessorTableRow>() {
          public int compare(ProcessorTableRow o1, ProcessorTableRow o2) {
            return o1.name.compareToIgnoreCase(o2.name);
          }
        });
        fireTableRowsInserted(0, processorMap.size()-1);
      }
    }

    public void clear() {
      final int count = myRows.size();
      if (count > 0) {
        myRows.clear();
        fireTableRowsDeleted(0, count-1);
      }
    }

    public Map<String, String> exportToMap() {
      final Map<String, String> map = new HashMap<String, String>();
      for (ProcessorTableRow row : myRows) {
        if (row.name != null) {
          final String name = row.name.trim();
          if (name.length() > 0 && !map.containsKey(name)) {
            map.put(name, row.options);
          }
        }
      }
      return map;
    }
  }

  private static final class ProcessorTableRow {
    public static final int NAME_COLUMN = 0;
    public static final int OPTIONS_COLUMN = 1;

    public String name = "";
    public String options = "";

    public ProcessorTableRow() {
    }

    public ProcessorTableRow(String name, String options) {
      this.name = name != null? name : "";
      this.options = options != null? options : "";
    }
  }
}

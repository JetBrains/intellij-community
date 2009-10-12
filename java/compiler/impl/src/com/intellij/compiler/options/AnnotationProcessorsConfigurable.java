package com.intellij.compiler.options;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.TableUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.ItemRemovable;
import com.intellij.util.ui.Table;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.TitledBorder;
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
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Oct 5, 2009
 */
public class AnnotationProcessorsConfigurable implements Configurable{
  private ElementsChooser<Module> myModulesChooser;
  private final Project myProject;
  private JRadioButton myRbClasspath;
  private JRadioButton myRbProcessorsPath;
  private TextFieldWithBrowseButton myProcessorPathField;
  private ProcessorTableModel myProcessorsModel;
  private JCheckBox myCbEnableProcessing;
  private JButton myRemoveButton;
  private Table myProcessorTable;
  private JButton myAddButton;

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
          FileChooser.chooseFiles(myProcessorPathField, new FileChooserDescriptor(true, true, true, true, false, true));
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
    processorTablePanel.setBorder(new TitledBorder("Annotation Processors"));
    myProcessorTable = new Table(myProcessorsModel);
    processorTablePanel.add(new JScrollPane(myProcessorTable), BorderLayout.CENTER);
    final JPanel buttons = new JPanel(new GridBagLayout());
    myAddButton = new JButton("Add");
    buttons.add(myAddButton, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(0, 5, 0, 0), 0, 0));
    myRemoveButton = new JButton("Remove");
    buttons.add(myRemoveButton, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(5, 5, 0, 0), 0, 0));
    processorTablePanel.add(buttons, BorderLayout.EAST);
    processorTablePanel.setPreferredSize(new Dimension(processorTablePanel.getPreferredSize().width, 50));

    myModulesChooser = new ElementsChooser<Module>(true) {
      protected String getItemText(@NotNull Module module) {
        return module.getName() + " (" + FileUtil.toSystemDependentName(module.getModuleFilePath()) + ")";
      }

      protected Icon getItemIcon(Module module) {
        return module.getModuleType().getNodeIcon(false);
      }
    };
    myModulesChooser.setBorder(BorderFactory.createTitledBorder("Processed Modules"));

    mainPanel.add(myCbEnableProcessing, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(5, 0, 0, 0), 0, 0));
    mainPanel.add(myRbClasspath, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(10, 0, 0, 0), 0, 0));
    mainPanel.add(myRbProcessorsPath, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(5, 0, 0, 0), 0, 0));
    mainPanel.add(myProcessorPathField, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(5, 5, 0, 0), 0, 0));
    mainPanel.add(processorTablePanel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(10, 0, 0, 0), 0, 0));
    mainPanel.add(myModulesChooser, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(10, 0, 0, 0), 0, 0));


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
    myAddButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final TableCellEditor cellEditor = myProcessorTable.getCellEditor();
        if (cellEditor != null) {
          cellEditor.stopCellEditing();
        }
        final ProcessorTableModel model = (ProcessorTableModel)myProcessorTable.getModel();
        final int inserdedIndex = model.addRow();
        TableUtil.editCellAt(myProcessorTable, inserdedIndex, ProcessorTableRow.NAME_COLUMN);
      }
    });

    myRemoveButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        TableUtil.removeSelectedItems(myProcessorTable);
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
    myRemoveButton.setEnabled(enabled && myProcessorTable.getSelectedRow() >= 0);
    myAddButton.setEnabled(enabled);
    myProcessorTable.setEnabled(enabled);
    final JTableHeader header = myProcessorTable.getTableHeader();
    if (header != null) {
      header.repaint();
    }
    myModulesChooser.setEnabled(enabled);
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

    if (!getExcludedModules().equals(config.getExcludedModules())) {
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

    config.setExcludedModules(getExcludedModules());
  }

  private Set<Module> getExcludedModules() {
    final Set<Module> excludedModules = new HashSet<Module>(Arrays.asList(ModuleManager.getInstance(myProject).getModules()));
    excludedModules.removeAll(new HashSet<Module>(myModulesChooser.getMarkedElements()));
    return excludedModules;
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
    
    // excludes
    final Set<Module> excludedModules = new HashSet<Module>(config.getExcludedModules());
    myModulesChooser.removeAllElements();
    for (final Module module : ModuleManager.getInstance(myProject).getModules()) {
      myModulesChooser.addElement(module, !excludedModules.contains(module));
    }
    myModulesChooser.sort(new Comparator<Module>() {
      public int compare(Module o1, Module o2) {
        return o1.getName().compareToIgnoreCase(o2.getName());
      }
    });
  }

  public void disposeUIResources() {
  }

  private static class ProcessorTableModel extends AbstractTableModel implements ItemRemovable{
    private final java.util.List<ProcessorTableRow> myRows = new ArrayList<ProcessorTableRow>();

    public String getColumnName(int column) {
      switch (column) {
        case ProcessorTableRow.NAME_COLUMN: return "Processor FQ Name";
        case ProcessorTableRow.OPTIONS_COLUMN : return "Processor Run Options";
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

    public int addRow() {
      myRows.add(new ProcessorTableRow());
      final int inserted = myRows.size() - 1;
      fireTableRowsInserted(inserted, inserted);
      return inserted;
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
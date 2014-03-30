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

package com.intellij.diagnostic.logging;

import com.intellij.diagnostic.DiagnosticBundle;
import com.intellij.execution.configurations.LogFileOptions;
import com.intellij.execution.configurations.PredefinedLogFile;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.CellEditorComponentWithBrowseButton;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: anna
 * Date: Apr 22, 2005
 */
public class LogConfigurationPanel<T extends RunConfigurationBase> extends SettingsEditor<T> {
  private final TableView<LogFileOptions> myFilesTable;
  private final ListTableModel<LogFileOptions> myModel;
  private JPanel myWholePanel;
  private JPanel myScrollPanel;
  private JBCheckBox myRedirectOutputCb;
  private TextFieldWithBrowseButton myOutputFile;
  private JCheckBox myShowConsoleOnStdOutCb;
  private JCheckBox myShowConsoleOnStdErrCb;
  private final Map<LogFileOptions, PredefinedLogFile> myLog2Predefined = new HashMap<LogFileOptions, PredefinedLogFile>();
  private final List<PredefinedLogFile> myUnresolvedPredefined = new ArrayList<PredefinedLogFile>();

  private final ColumnInfo<LogFileOptions, Boolean> IS_SHOW = new MyIsActiveColumnInfo();
  private final ColumnInfo<LogFileOptions, LogFileOptions> FILE = new MyLogFileColumnInfo();
  private final ColumnInfo<LogFileOptions, Boolean> IS_SKIP_CONTENT = new MyIsSkipColumnInfo();

  public LogConfigurationPanel() {
    myModel = new ListTableModel<LogFileOptions>(IS_SHOW, FILE, IS_SKIP_CONTENT);
    myFilesTable = new TableView<LogFileOptions>(myModel);
    myFilesTable.getEmptyText().setText(DiagnosticBundle.message("log.monitor.no.files"));

    final JTableHeader tableHeader = myFilesTable.getTableHeader();
    final FontMetrics fontMetrics = tableHeader.getFontMetrics(tableHeader.getFont());

    int preferredWidth = fontMetrics.stringWidth(IS_SHOW.getName()) + 20;
    setUpColumnWidth(tableHeader, preferredWidth, 0);

    preferredWidth = fontMetrics.stringWidth(IS_SKIP_CONTENT.getName()) + 20;
    setUpColumnWidth(tableHeader, preferredWidth, 2);

    myFilesTable.setColumnSelectionAllowed(false);
    myFilesTable.setShowGrid(false);
    myFilesTable.setDragEnabled(false);
    myFilesTable.setShowHorizontalLines(false);
    myFilesTable.setShowVerticalLines(false);
    myFilesTable.setIntercellSpacing(new Dimension(0, 0));

    myScrollPanel.add(
      ToolbarDecorator.createDecorator(myFilesTable)
        .setAddAction(new AnActionButtonRunnable() {
          @Override
          public void run(AnActionButton button) {
            ArrayList<LogFileOptions> newList = new ArrayList<LogFileOptions>(myModel.getItems());
            LogFileOptions newOptions = new LogFileOptions("", "", true, true, false);
            if (showEditorDialog(newOptions)) {
              newList.add(newOptions);
              myModel.setItems(newList);
              int index = myModel.getRowCount() - 1;
              myModel.fireTableRowsInserted(index, index);
              myFilesTable.setRowSelectionInterval(index, index);
            }
          }
        }).setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          TableUtil.stopEditing(myFilesTable);
          final int[] selected = myFilesTable.getSelectedRows();
          if (selected == null || selected.length == 0) return;
          for (int i = selected.length - 1; i >= 0; i--) {
            myModel.removeRow(selected[i]);
          }
          for (int i = selected.length - 1; i >= 0; i--) {
            int idx = selected[i];
            myModel.fireTableRowsDeleted(idx, idx);
          }
          int selection = selected[0];
          if (selection >= myModel.getRowCount()) {
            selection = myModel.getRowCount() - 1;
          }
          if (selection >= 0) {
            myFilesTable.setRowSelectionInterval(selection, selection);
          }
          myFilesTable.requestFocus();
        }
      }).setEditAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          final int selectedRow = myFilesTable.getSelectedRow();
          final LogFileOptions selectedOptions = myFilesTable.getSelectedObject();
          showEditorDialog(selectedOptions);
          myModel.fireTableDataChanged();
          myFilesTable.setRowSelectionInterval(selectedRow, selectedRow);
        }
      }).setRemoveActionUpdater(new AnActionButtonUpdater() {
        @Override
        public boolean isEnabled(AnActionEvent e) {
          return myFilesTable.getSelectedRowCount() >= 1 &&
                 !myLog2Predefined.containsKey(myFilesTable.getSelectedObject());
        }
      }).setEditActionUpdater(new AnActionButtonUpdater() {
        @Override
        public boolean isEnabled(AnActionEvent e) {
          return myFilesTable.getSelectedRowCount() >= 1 &&
                 !myLog2Predefined.containsKey(myFilesTable.getSelectedObject()) &&
                 myFilesTable.getSelectedObject() != null;
        }
      }).disableUpDownActions().createPanel(), BorderLayout.CENTER);

    myWholePanel.setPreferredSize(new Dimension(-1, 150));
    myOutputFile.addBrowseFolderListener("Choose File to Save Console Output", "Console output would be saved to the specified file", null,
                                         FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor(),
                                         TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);
    myRedirectOutputCb.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myOutputFile.setEnabled(myRedirectOutputCb.isSelected());
      }
    });
  }

  private void setUpColumnWidth(final JTableHeader tableHeader, final int preferredWidth, int columnIdx) {
    myFilesTable.getColumnModel().getColumn(columnIdx).setCellRenderer(new BooleanTableCellRenderer());
    final TableColumn tableColumn = tableHeader.getColumnModel().getColumn(columnIdx);
    tableColumn.setWidth(preferredWidth);
    tableColumn.setPreferredWidth(preferredWidth);
    tableColumn.setMinWidth(preferredWidth);
    tableColumn.setMaxWidth(preferredWidth);
  }

  public void refreshPredefinedLogFiles(RunConfigurationBase configurationBase) {
    List<LogFileOptions> items = myModel.getItems();
    List<LogFileOptions> newItems = new ArrayList<LogFileOptions>();
    boolean changed = false;
    for (LogFileOptions item : items) {
      final PredefinedLogFile predefined = myLog2Predefined.get(item);
      if (predefined != null) {
        final LogFileOptions options = configurationBase.getOptionsForPredefinedLogFile(predefined);
        if (LogFileOptions.areEqual(item, options)) {
          newItems.add(item);
        }
        else {
          changed = true;
          myLog2Predefined.remove(item);
          if (options == null) {
            myUnresolvedPredefined.add(predefined);
          }
          else {
            newItems.add(options);
            myLog2Predefined.put(options, predefined);
          }
        }
      }
      else {
        newItems.add(item);
      }
    }

    final PredefinedLogFile[] unresolved = myUnresolvedPredefined.toArray(new PredefinedLogFile[myUnresolvedPredefined.size()]);
    for (PredefinedLogFile logFile : unresolved) {
      final LogFileOptions options = configurationBase.getOptionsForPredefinedLogFile(logFile);
      if (options != null) {
        changed = true;
        myUnresolvedPredefined.remove(logFile);
        myLog2Predefined.put(options, logFile);
        newItems.add(options);
      }
    }

    if (changed) {
      myModel.setItems(newItems);
    }
  }

  @Override
  protected void resetEditorFrom(final RunConfigurationBase configuration) {
    ArrayList<LogFileOptions> list = new ArrayList<LogFileOptions>();
    final ArrayList<LogFileOptions> logFiles = configuration.getLogFiles();
    for (LogFileOptions setting : logFiles) {
      list.add(
        new LogFileOptions(setting.getName(), setting.getPathPattern(), setting.isEnabled(), setting.isSkipContent(), setting.isShowAll()));
    }
    myLog2Predefined.clear();
    myUnresolvedPredefined.clear();
    final ArrayList<PredefinedLogFile> predefinedLogFiles = configuration.getPredefinedLogFiles();
    for (PredefinedLogFile predefinedLogFile : predefinedLogFiles) {
      PredefinedLogFile logFile = new PredefinedLogFile(predefinedLogFile);
      final LogFileOptions options = configuration.getOptionsForPredefinedLogFile(logFile);
      if (options != null) {
        myLog2Predefined.put(options, logFile);
        list.add(options);
      }
      else {
        myUnresolvedPredefined.add(logFile);
      }
    }
    myModel.setItems(list);
    final boolean redirectOutputToFile = configuration.isSaveOutputToFile();
    myRedirectOutputCb.setSelected(redirectOutputToFile);
    final String fileOutputPath = configuration.getOutputFilePath();
    myOutputFile.setText(fileOutputPath != null ? FileUtil.toSystemDependentName(fileOutputPath) : "");
    myOutputFile.setEnabled(redirectOutputToFile);
    myShowConsoleOnStdOutCb.setSelected(configuration.isShowConsoleOnStdOut());
    myShowConsoleOnStdErrCb.setSelected(configuration.isShowConsoleOnStdErr());
  }

  @Override
  protected void applyEditorTo(final RunConfigurationBase configuration) throws ConfigurationException {
    myFilesTable.stopEditing();
    configuration.removeAllLogFiles();
    configuration.removeAllPredefinedLogFiles();

    for (int i = 0; i < myModel.getRowCount(); i++) {
      LogFileOptions options = (LogFileOptions)myModel.getValueAt(i, 1);
      if (Comparing.equal(options.getPathPattern(), "")) {
        continue;
      }
      final Boolean checked = (Boolean)myModel.getValueAt(i, 0);
      final Boolean skipped = (Boolean)myModel.getValueAt(i, 2);
      final PredefinedLogFile predefined = myLog2Predefined.get(options);
      if (predefined != null) {
        configuration.addPredefinedLogFile(new PredefinedLogFile(predefined.getId(), options.isEnabled()));
      }
      else {
        configuration
          .addLogFile(options.getPathPattern(), options.getName(), checked.booleanValue(), skipped.booleanValue(), options.isShowAll());
      }
    }
    for (PredefinedLogFile logFile : myUnresolvedPredefined) {
      configuration.addPredefinedLogFile(logFile);
    }
    final String text = myOutputFile.getText();
    configuration.setFileOutputPath(StringUtil.isEmpty(text) ? null : FileUtil.toSystemIndependentName(text));
    configuration.setSaveOutputToFile(myRedirectOutputCb.isSelected());
    configuration.setShowConsoleOnStdOut(myShowConsoleOnStdOutCb.isSelected());
    configuration.setShowConsoleOnStdErr(myShowConsoleOnStdErrCb.isSelected());
  }

  @Override
  @NotNull
  protected JComponent createEditor() {
    return myWholePanel;
  }

  private static boolean showEditorDialog(@NotNull LogFileOptions options) {
    EditLogPatternDialog dialog = new EditLogPatternDialog();
    dialog.init(options.getName(), options.getPathPattern(), options.isShowAll());
    dialog.show();
    if (dialog.isOK()) {
      options.setName(dialog.getName());
      options.setPathPattern(dialog.getLogPattern());
      options.setShowAll(dialog.isShowAllFiles());
      return true;
    }
    return false;
  }

  private class MyLogFileColumnInfo extends ColumnInfo<LogFileOptions, LogFileOptions> {
    public MyLogFileColumnInfo() {
      super(DiagnosticBundle.message("log.monitor.log.file.column"));
    }

    @Override
    public TableCellRenderer getRenderer(final LogFileOptions p0) {
      return new DefaultTableCellRenderer() {
        @Override
        public Component getTableCellRendererComponent(JTable table,
                                                       Object value,
                                                       boolean isSelected,
                                                       boolean hasFocus,
                                                       int row,
                                                       int column) {
          final Component renderer = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
          setText(((LogFileOptions)value).getName());
          setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
          setBorder(null);
          return renderer;
        }
      };
    }

    @Override
    public LogFileOptions valueOf(final LogFileOptions object) {
      return object;
    }

    @Override
    public TableCellEditor getEditor(final LogFileOptions item) {
      return new LogFileCellEditor(item);
    }

    @Override
    public void setValue(final LogFileOptions o, final LogFileOptions aValue) {
      if (aValue != null) {
        if (!o.getName().equals(aValue.getName()) || !o.getPathPattern().equals(aValue.getPathPattern())
            || o.isShowAll() != aValue.isShowAll()) {
          myLog2Predefined.remove(o);
        }
        o.setName(aValue.getName());
        o.setShowAll(aValue.isShowAll());
        o.setPathPattern(aValue.getPathPattern());
      }
    }

    @Override
    public boolean isCellEditable(final LogFileOptions o) {
      return !myLog2Predefined.containsKey(o);
    }
  }

  private class MyIsActiveColumnInfo extends ColumnInfo<LogFileOptions, Boolean> {
    protected MyIsActiveColumnInfo() {
      super(DiagnosticBundle.message("log.monitor.is.active.column"));
    }

    @Override
    public Class getColumnClass() {
      return Boolean.class;
    }

    @Override
    public Boolean valueOf(final LogFileOptions object) {
      return object.isEnabled();
    }

    @Override
    public boolean isCellEditable(LogFileOptions element) {
      return true;
    }

    @Override
    public void setValue(LogFileOptions element, Boolean checked) {
      final PredefinedLogFile predefinedLogFile = myLog2Predefined.get(element);
      if (predefinedLogFile != null) {
        predefinedLogFile.setEnabled(checked.booleanValue());
      }
      element.setEnable(checked.booleanValue());
    }
  }

  private class MyIsSkipColumnInfo extends ColumnInfo<LogFileOptions, Boolean> {
    protected MyIsSkipColumnInfo() {
      super(DiagnosticBundle.message("log.monitor.is.skipped.column"));
    }

    @Override
    public Class getColumnClass() {
      return Boolean.class;
    }

    @Override
    public Boolean valueOf(final LogFileOptions element) {
      return element.isSkipContent();
    }

    @Override
    public boolean isCellEditable(LogFileOptions element) {
      return !myLog2Predefined.containsKey(element);
    }

    @Override
    public void setValue(LogFileOptions element, Boolean skipped) {
      element.setSkipContent(skipped.booleanValue());
    }
  }

  private class LogFileCellEditor extends AbstractTableCellEditor {
    private final CellEditorComponentWithBrowseButton<JTextField> myComponent;
    private final LogFileOptions myLogFileOptions;

    public LogFileCellEditor(LogFileOptions options) {
      myLogFileOptions = options;
      myComponent = new CellEditorComponentWithBrowseButton<JTextField>(new TextFieldWithBrowseButton(), this);
      getChildComponent().setEditable(false);
      getChildComponent().setBorder(null);
      myComponent.getComponentWithButton().getButton().addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          showEditorDialog(myLogFileOptions);
          JTextField textField = getChildComponent();
          textField.setText(myLogFileOptions.getName());
          textField.requestFocus();
          myModel.fireTableDataChanged();
        }
      });
    }

    @Override
    public Object getCellEditorValue() {
      return myLogFileOptions;
    }

    private JTextField getChildComponent() {
      return myComponent.getChildComponent();
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
      getChildComponent().setText(((LogFileOptions)value).getName());
      return myComponent;
    }
  }
}

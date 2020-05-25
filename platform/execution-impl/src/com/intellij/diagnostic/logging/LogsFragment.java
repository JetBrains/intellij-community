// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic.logging;

import com.intellij.diagnostic.DiagnosticBundle;
import com.intellij.execution.configurations.LogFileOptions;
import com.intellij.execution.configurations.PredefinedLogFile;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.ui.NestedGroupFragment;
import com.intellij.execution.ui.SettingsEditorFragment;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.BooleanTableCellRenderer;
import com.intellij.ui.TableUtil;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.TableView;
import com.intellij.util.SmartList;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.CellEditorComponentWithBrowseButton;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class LogsFragment<T extends RunConfigurationBase<?>> extends NestedGroupFragment<T> {

  private final Map<LogFileOptions, PredefinedLogFile> myLog2Predefined = new THashMap<>();
  private final List<PredefinedLogFile> myUnresolvedPredefined = new SmartList<>();
  private final TableView<LogFileOptions> myFilesTable;
  private final ListTableModel<LogFileOptions> myModel;

  public LogsFragment() {
    super("log.monitor", DiagnosticBundle.message("log.monitor.fragment.name"), DiagnosticBundle.message("log.monitor.fragment.group"));

    ColumnInfo<LogFileOptions, Boolean> IS_SHOW = new MyIsActiveColumnInfo();
    ColumnInfo<LogFileOptions, LogFileOptions> FILE = new MyLogFileColumnInfo();
    ColumnInfo<LogFileOptions, Boolean> IS_SKIP_CONTENT = new MyIsSkipColumnInfo();

    myModel = new ListTableModel<>(IS_SHOW, FILE, IS_SKIP_CONTENT);
    myFilesTable = new TableView<>(myModel);
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

    myComponent = ToolbarDecorator.createDecorator(myFilesTable)
      .setAddAction(button -> {
        ArrayList<LogFileOptions> newList = new ArrayList<>(myModel.getItems());
        LogFileOptions newOptions = new LogFileOptions("", "", true);
        if (showEditorDialog(newOptions)) {
          newList.add(newOptions);
          myModel.setItems(newList);
          int index = myModel.getRowCount() - 1;
          myModel.fireTableRowsInserted(index, index);
          myFilesTable.setRowSelectionInterval(index, index);
        }
      }).setRemoveAction(button -> {
        TableUtil.stopEditing(myFilesTable);
        final int[] selected = myFilesTable.getSelectedRows();
        if (selected.length == 0) return;
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
        IdeFocusManager.getGlobalInstance()
          .doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myFilesTable, true));
      }).setEditAction(button -> {
        final int selectedRow = myFilesTable.getSelectedRow();
        //noinspection ConstantConditions
        showEditorDialog(myFilesTable.getSelectedObject());
        myModel.fireTableDataChanged();
        myFilesTable.setRowSelectionInterval(selectedRow, selectedRow);
      }).setRemoveActionUpdater(e -> myFilesTable.getSelectedRowCount() >= 1 &&
                                     !myLog2Predefined.containsKey(myFilesTable.getSelectedObject())).setEditActionUpdater(
        e -> myFilesTable.getSelectedRowCount() >= 1 &&
             !myLog2Predefined.containsKey(myFilesTable.getSelectedObject()) &&
             myFilesTable.getSelectedObject() != null).disableUpDownActions().createPanel();
  }

  private void setUpColumnWidth(final JTableHeader tableHeader, final int preferredWidth, int columnIdx) {
    myFilesTable.getColumnModel().getColumn(columnIdx).setCellRenderer(new BooleanTableCellRenderer());
    final TableColumn tableColumn = tableHeader.getColumnModel().getColumn(columnIdx);
    tableColumn.setWidth(preferredWidth);
    tableColumn.setPreferredWidth(preferredWidth);
    tableColumn.setMinWidth(preferredWidth);
    tableColumn.setMaxWidth(preferredWidth);
  }

  @Override
  protected void resetEditorFrom(@NotNull T configuration) {
    super.resetEditorFrom(configuration);
    ArrayList<LogFileOptions> list = new ArrayList<>();
    final List<LogFileOptions> logFiles = configuration.getLogFiles();
    for (LogFileOptions setting : logFiles) {
      list.add(
        new LogFileOptions(setting.getName(), setting.getPathPattern(), setting.isEnabled(), setting.isSkipContent(), setting.isShowAll()));
    }
    myLog2Predefined.clear();
    myUnresolvedPredefined.clear();
    final List<PredefinedLogFile> predefinedLogFiles = configuration.getPredefinedLogFiles();
    for (PredefinedLogFile predefinedLogFile : predefinedLogFiles) {
      PredefinedLogFile logFile = new PredefinedLogFile();
      logFile.copyFrom(predefinedLogFile);
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
  }

  @Override
  protected void applyEditorTo(@NotNull T configuration) throws ConfigurationException {
    super.applyEditorTo(configuration);
    myFilesTable.stopEditing();
    configuration.removeAllLogFiles();
    configuration.removeAllPredefinedLogFiles();

    for (int i = 0; i < myModel.getRowCount(); i++) {
      LogFileOptions options = (LogFileOptions)myModel.getValueAt(i, 1);
      if (Objects.equals(options.getPathPattern(), "")) {
        continue;
      }
      final Boolean checked = (Boolean)myModel.getValueAt(i, 0);
      final Boolean skipped = (Boolean)myModel.getValueAt(i, 2);
      final PredefinedLogFile predefined = myLog2Predefined.get(options);
      if (predefined != null) {
        PredefinedLogFile file = new PredefinedLogFile();
        file.setId(predefined.getId());
        file.setEnabled(options.isEnabled());
        configuration.addPredefinedLogFile(file);
      }
      else {
        configuration
          .addLogFile(options.getPathPattern(), options.getName(), checked.booleanValue(), skipped.booleanValue(), options.isShowAll());
      }
    }
    for (PredefinedLogFile logFile : myUnresolvedPredefined) {
      configuration.addPredefinedLogFile(logFile);
    }
  }

  @Override
  public List<SettingsEditorFragment<T, ?>> createChildren() {
    SettingsEditorFragment<T, JButton> stdOut = SettingsEditorFragment
      .createTag("xxx", DiagnosticBundle.message("log.monitor.fragment.stdout"), null, t -> t.isShowConsoleOnStdOut(),
                 (t, value) -> t.setShowConsoleOnStdOut(value));
    SettingsEditorFragment<T, JButton> stdErr = SettingsEditorFragment
      .createTag("xxx", DiagnosticBundle.message("log.monitor.fragment.stderr"), null, t -> t.isShowConsoleOnStdErr(),
                 (t, value) -> t.setShowConsoleOnStdErr(value));
    return Arrays.asList(stdOut, stdErr);
  }

  @Override
  public @Nullable String getChildrenGroupName() {
    return DiagnosticBundle.message("log.monitor.fragment.settings");
  }

  private class MyLogFileColumnInfo extends ColumnInfo<LogFileOptions, LogFileOptions> {
    MyLogFileColumnInfo() {
      super(DiagnosticBundle.message("log.monitor.log.file.column"));
    }

    @Override
    public TableCellRenderer getRenderer(final LogFileOptions p0) {
      return new DefaultTableCellRenderer() {
        @NotNull
        @Override
        public Component getTableCellRendererComponent(@NotNull JTable table,
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
      element.setEnabled(checked.booleanValue());
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

    LogFileCellEditor(LogFileOptions options) {
      myLogFileOptions = options;
      myComponent = new CellEditorComponentWithBrowseButton<>(new TextFieldWithBrowseButton(), this);
      getChildComponent().setEditable(false);
      getChildComponent().setBorder(null);
      myComponent.getComponentWithButton().getButton().addActionListener(e -> {
        showEditorDialog(myLogFileOptions);
        JTextField textField = getChildComponent();
        textField.setText(myLogFileOptions.getName());
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(textField, true));
        myModel.fireTableDataChanged();
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

  private static boolean showEditorDialog(@NotNull LogFileOptions options) {
    EditLogPatternDialog dialog = new EditLogPatternDialog();
    dialog.init(options.getName(), options.getPathPattern(), options.isShowAll());
    if (dialog.showAndGet()) {
      options.setName(dialog.getName());
      options.setPathPattern(dialog.getLogPattern());
      options.setShowAll(dialog.isShowAllFiles());
      return true;
    }
    return false;
  }
}

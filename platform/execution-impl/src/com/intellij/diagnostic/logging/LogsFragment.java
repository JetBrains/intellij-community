// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic.logging;

import com.intellij.diagnostic.DiagnosticBundle;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.LogFileOptions;
import com.intellij.execution.configurations.PredefinedLogFile;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.ui.SettingsEditorFragment;
import com.intellij.openapi.actionSystem.ActionToolbarPosition;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.BooleanTableCellRenderer;
import com.intellij.ui.TableUtil;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.TableView;
import com.intellij.util.SmartList;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.LocalPathCellEditor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.List;
import java.util.*;

@ApiStatus.Internal
public final class LogsFragment<T extends RunConfigurationBase<?>> extends SettingsEditorFragment<T, JComponent> {
  private final Map<LogFileOptions, PredefinedLogFile> myLog2Predefined = new HashMap<>();
  private final List<PredefinedLogFile> myUnresolvedPredefined = new SmartList<>();
  private final TableView<LogFileOptions> myFilesTable;
  private final ListTableModel<LogFileOptions> myModel;

  public LogsFragment() {
    super("log.monitor",
          DiagnosticBundle.message("log.monitor.fragment.name"), null, null, null, null,
          t -> !t.getLogFiles().isEmpty());
    setActionHint(ExecutionBundle.message("the.ide.will.display.the.selected.logs.in.the.run.tool.window"));

    ColumnInfo<LogFileOptions, String> TAB_NAME = new TabNameColumnInfo();
    ColumnInfo<LogFileOptions, String> FILE = new FileColumnInfo();
    ColumnInfo<LogFileOptions, Boolean> IS_SHOW = new MyIsActiveColumnInfo();
    ColumnInfo<LogFileOptions, Boolean> IS_SKIP_CONTENT = new MyIsSkipColumnInfo();

    myModel = new ListTableModel<>(TAB_NAME, FILE, IS_SHOW, IS_SKIP_CONTENT);
    myFilesTable = new TableView<>(myModel);
    myFilesTable.getEmptyText().setText(DiagnosticBundle.message("log.monitor.no.files"));

    final JTableHeader tableHeader = myFilesTable.getTableHeader();
    final FontMetrics fontMetrics = tableHeader.getFontMetrics(tableHeader.getFont());

    int preferredWidth = fontMetrics.stringWidth(IS_SHOW.getName()) + 20;
    setUpColumnWidth(tableHeader, preferredWidth, 2);

    preferredWidth = fontMetrics.stringWidth(IS_SKIP_CONTENT.getName()) + 20;
    setUpColumnWidth(tableHeader, preferredWidth, 3);

    setUpColumnWidth(tableHeader, 100, 0);

    myFilesTable.setColumnSelectionAllowed(false);
    myFilesTable.setShowGrid(false);
    myFilesTable.setDragEnabled(false);
    myFilesTable.setShowHorizontalLines(false);
    myFilesTable.setShowVerticalLines(false);
    myFilesTable.setIntercellSpacing(new Dimension(0, 0));
    myFilesTable.setupEasyFocusTraversing();

    myComponent = ToolbarDecorator.createDecorator(myFilesTable)
      .setToolbarPosition(ActionToolbarPosition.BOTTOM)
      .setAddAction(button -> {
        List<LogFileOptions> newList = new ArrayList<>(myModel.getItems());
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
    List<LogFileOptions> list = new ArrayList<>();
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
  protected void applyEditorTo(@NotNull T configuration) {
    configuration.removeAllLogFiles();
    configuration.removeAllPredefinedLogFiles();
    if (!isSelected()) return;
    for (int i = 0; i < myModel.getRowCount(); i++) {
      LogFileOptions options = myModel.getItem(i);
      if (Objects.equals(options.getPathPattern(), "")) {
        continue;
      }
      final Boolean checked = (Boolean)myModel.getValueAt(i, 2);
      final Boolean skipped = (Boolean)myModel.getValueAt(i, 3);
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

  private final class TabNameColumnInfo extends ColumnInfo<LogFileOptions, String> {
    TabNameColumnInfo() {
      super(DiagnosticBundle.message("log.monitor.tab.name.column"));
    }

    @Override
    public @Nullable TableCellRenderer getRenderer(LogFileOptions options) {
      return new DefaultTableCellRenderer();
    }

    @Override
    public String valueOf(final LogFileOptions object) {
      return object.getName();
    }

    @Override
    public void setValue(LogFileOptions options, String value) {
      options.setName(value);
    }

    @Override
    public boolean isCellEditable(final LogFileOptions o) {
      return !myLog2Predefined.containsKey(o);
    }
  }

  private final class FileColumnInfo extends ColumnInfo<LogFileOptions, String> {
    FileColumnInfo() {
      super(DiagnosticBundle.message("log.monitor.file.column"));
    }

    @Override
    public String valueOf(final LogFileOptions object) {
      return object.getPathPattern();
    }

    @Override
    public void setValue(LogFileOptions options, String value) {
      options.setPathPattern(value);
    }

    @Override
    public boolean isCellEditable(final LogFileOptions o) {
      return !myLog2Predefined.containsKey(o);
    }

    @Override
    public @Nullable TableCellEditor getEditor(LogFileOptions options) {
      return new LocalPathCellEditor();
    }
  }

  private final class MyIsActiveColumnInfo extends ColumnInfo<LogFileOptions, Boolean> {
    private MyIsActiveColumnInfo() {
      super(DiagnosticBundle.message("log.monitor.is.active.column"));
    }

    @Override
    public Class<?> getColumnClass() {
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

  private final class MyIsSkipColumnInfo extends ColumnInfo<LogFileOptions, Boolean> {
    private MyIsSkipColumnInfo() {
      super(DiagnosticBundle.message("log.monitor.is.skipped.column"));
    }

    @Override
    public Class<?> getColumnClass() {
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

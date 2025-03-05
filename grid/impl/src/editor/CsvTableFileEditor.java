package com.intellij.database.editor;

import com.intellij.database.csv.CsvFormat;
import com.intellij.database.csv.CsvFormatEditor;
import com.intellij.database.csv.CsvFormatResolver;
import com.intellij.database.csv.CsvFormatter;
import com.intellij.database.datagrid.*;
import com.intellij.database.run.ui.DataGridRequestPlace;
import com.intellij.database.vfs.fragment.CsvTableDataFragmentFile;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Gregory.Shrago
 */
public class CsvTableFileEditor extends TableFileEditor implements CsvFormatEditor {
  private final DataGrid myDataGrid;

  public CsvTableFileEditor(@NotNull Project project, @NotNull CsvTableDataFragmentFile file) {
    super(project, file.getOriginalFile());
    CsvDocumentDataHookUp hookUp = GridDataHookUpManager.getInstance(project).getHookUp(file, this);
    myDataGrid = createDataGrid(hookUp);
    GridUtil.addGridHeaderComponent(myDataGrid);
  }

  public CsvTableFileEditor(@NotNull Project project, @NotNull VirtualFile file, @NotNull CsvFormat format) {
    super(project, file);
    CsvDocumentDataHookUp hookUp = GridDataHookUpManager.getInstance(project).getHookUp(file, format, this);
    myDataGrid = createDataGrid(hookUp);
    GridUtil.addGridHeaderComponent(myDataGrid);
  }

  @Override
  protected void uiDataSnapshot(@NotNull DataSink sink) {
    super.uiDataSnapshot(sink);
    sink.set(CSV_FORMAT_EDITOR_KEY, this);
  }

  @Override
  public @NotNull FileEditorState getState(@NotNull FileEditorStateLevel level) {
    CsvDocumentDataHookUp hookup = getHookup();
    return getState(level, hookup);
  }

  @Override
  protected void configure(@NotNull DataGrid grid, @NotNull DataGridAppearance appearance) {
    GridUtil.configureCsvTable(grid, appearance);
  }

  public static @NotNull FileEditorState getState(@NotNull FileEditorStateLevel level, @NotNull CsvDocumentDataHookUp hookup) {
    return level == FileEditorStateLevel.FULL && hookup.getRange() == null ? new CsvFormatResolver.State(hookup.getFormat()) : FileEditorState.INSTANCE;
  }

  @Override
  public void setState(@NotNull FileEditorState state) {
    CsvFormat format = CsvFormatResolver.readCsvFormat(state);
    if (format != null && !format.equals(getHookup().getFormat())) {
      setFormat(format);
    }
  }

  @Override
  public @NotNull DataGrid getDataGrid() {
    return myDataGrid;
  }

  public void setFormat(@NotNull CsvFormat format) {
    getHookup().setFormat(format, new GridRequestSource(new DataGridRequestPlace(myDataGrid)));
  }

  private @NotNull CsvDocumentDataHookUp getHookup() {
    return (CsvDocumentDataHookUp)myDataGrid.getDataHookup();
  }

  @Override
  public boolean firstRowIsHeader() {
    return getHookup().getFormat().headerRecord != null;
  }

  @Override
  public void setFirstRowIsHeader(boolean value) {
    setFormat(CsvFormatter.setFirstRowIsHeader(getHookup().getFormat(), value));
  }
}

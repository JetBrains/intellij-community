package com.intellij.database.run.actions;

import com.intellij.database.datagrid.*;
import com.intellij.database.extractors.ObjectFormatter;
import com.intellij.database.run.ui.DataAccessType;
import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.intellij.database.datagrid.GridUtil.createFormatterConfig;

public class CompareCellsAction extends AnAction implements DumbAware {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    DataGrid grid = GridUtil.getDataGrid(e.getDataContext());
    SelectionModel<GridRow, GridColumn> model = grid == null ? null : grid.getSelectionModel();
    int cells = model == null ? 0 : model.getSelectedColumns().size() * model.getSelectedRows().size();
    e.getPresentation().setEnabledAndVisible(cells > 1 && cells <= 3);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataGrid grid = GridUtil.getDataGrid(e.getDataContext());
    if (grid == null) return;

    SelectionModel<GridRow, GridColumn> model = grid.getSelectionModel();
    ModelIndexSet<GridColumn> columns = model.getSelectedColumns();
    ModelIndexSet<GridRow> rows = model.getSelectedRows();
    List<Cell> cells = getCells(rows, columns, grid);

    DiffContentFactory factory = DiffContentFactory.getInstance();
    List<DiffContent> contents = ContainerUtil.map(cells, v -> factory.create(v.value, v.language.getAssociatedFileType()));
    List<String> titles = ContainerUtil.map(cells, v -> v.title);
    String tableName = GridHelper.get(grid).getTableName(grid);
    String title = StringUtil.join(titles, " vs ") + (tableName == null ? null : " (" + tableName + ")"); //NON-NLS

    SimpleDiffRequest request = new SimpleDiffRequest(title, contents, titles);
    DiffManager.getInstance().showDiff(e.getProject(), request);
  }

  private static @NotNull List<Cell> getCells(@NotNull ModelIndexSet<GridRow> rowsIdxs,
                                              @NotNull ModelIndexSet<GridColumn> columnsIdxs,
                                              @NotNull DataGrid grid) {
    GridModel<GridRow, GridColumn> dataModel = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS);
    ObjectFormatter formatter = grid.getObjectFormatter();
    ArrayList<Cell> result = new ArrayList<>();
    for (ModelIndex<GridColumn> columnIdx : columnsIdxs.asIterable()) {
      GridColumn column = Objects.requireNonNull(dataModel.getColumn(columnIdx));
      for (ModelIndex<GridRow> rowIdx : rowsIdxs.asIterable()) {
        GridRow row = Objects.requireNonNull(dataModel.getRow(rowIdx));
        Object value = dataModel.getValueAt(rowIdx, columnIdx);
        String stringValue = formatter.objectToString(value, column, createFormatterConfig(grid, columnIdx));
        stringValue = Objects.requireNonNullElse(stringValue, "null");
        String title = column.getName() + ":" + row.getRowNum();
        Language language = grid.getContentLanguage(columnIdx);
        result.add(new Cell(stringValue, title, language));
      }
    }
    return result;
  }

  private static class Cell {
    private final String value;
    private final String title;
    private final Language language;

    Cell(@NotNull String value, @NotNull String title, @NotNull Language language) {
      this.value = value;
      this.title = title;
      this.language = language;
    }
  }
}

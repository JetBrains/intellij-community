package com.intellij.database.run.actions;

import com.intellij.database.DataGridBundle;
import com.intellij.database.DatabaseDataKeys;
import com.intellij.database.datagrid.*;
import com.intellij.database.extractors.BinaryDisplayType;
import com.intellij.database.extractors.DisplayType;
import com.intellij.database.extractors.NumberDisplayType;
import com.intellij.database.extractors.ObjectFormatterUtil;
import com.intellij.database.run.ui.grid.editors.GridCellEditorHelper;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.NlsActions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

import static com.intellij.database.run.ui.DataAccessType.DATA_WITH_MUTATIONS;

public class ChangeColumnDisplayTypeAction extends ActionGroup implements DumbAware {

  public ChangeColumnDisplayTypeAction() {
    setPopup(true);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    DataGrid grid = e.getData(DatabaseDataKeys.DATA_GRID_KEY);
    if (grid != null) {
      ModelIndex<GridColumn> column = getColumn(e);
      if (column != null) {
        update(e, grid, column);
      }
      else {
        e.getPresentation().setEnabledAndVisible(false);
      }
    }
    else {
      e.getPresentation().setEnabledAndVisible(false);
    }
  }

  private static @Nullable ModelIndex<GridColumn> getColumn(@Nullable AnActionEvent e) {
    if (e == null) return null;
    DataGrid grid = e.getData(DatabaseDataKeys.DATA_GRID_KEY);
    if (grid == null) return null;
    ModelIndex<GridColumn> columnIdx = grid.getContextColumn();
    if (columnIdx.asInteger() == -1) {
      columnIdx = grid.getSelectionModel().getLeadSelectionColumn();
    }
    return columnIdx.asInteger() == -1 ? null : columnIdx;
  }

  private static void update(@NotNull AnActionEvent e, @NotNull DataGrid grid, @NotNull ModelIndex<GridColumn> columnIdx) {
    e.getPresentation().setText(DataGridBundle.message("group.Console.TableResult.ColumnDisplayTypeChange.text"));
    e.getPresentation().setEnabledAndVisible(isBinary(columnIdx, grid) || isIntegerOrBigInt(columnIdx, grid));
  }

  public static boolean isIntegerOrBigInt(@NotNull ModelIndex<GridColumn> columnIdx, @NotNull CoreGrid<GridRow, GridColumn> grid) {
    int type = GridCellEditorHelper.get(grid).guessJdbcTypeForEditing(grid, null, columnIdx);
    return ObjectFormatterUtil.isIntegerOrBigInt(type);
  }

  public static boolean isBinary(@NotNull ModelIndex<GridColumn> columnIdx, @NotNull CoreGrid<GridRow, GridColumn> grid) {
    int type = GridCellEditorHelper.get(grid).guessJdbcTypeForEditing(grid, null, columnIdx);
    GridColumn column = grid.getDataModel(DATA_WITH_MUTATIONS).getColumn(columnIdx);
    return ObjectFormatterUtil.isBinary(column, type);
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    if (e == null) return AnAction.EMPTY_ARRAY;
    ModelIndex<GridColumn> columnIdx = getColumn(e);
    DataGrid grid = e.getData(DatabaseDataKeys.DATA_GRID_KEY);
    if (grid == null || columnIdx == null) return AnAction.EMPTY_ARRAY;

    if (isBinary(columnIdx, grid)) {
      BinaryDisplayType detectedDisplayType = grid.getOptimalBinaryDisplayTypeForDetect(columnIdx);
      String displayTypeName = detectedDisplayType == BinaryDisplayType.DETECT ? "" : " (" + detectedDisplayType.getName() + ")";
      return Arrays.asList(
        new DisplayTypeAction(true, grid, columnIdx, BinaryDisplayType.DETECT,
                              DataGridBundle.message("action.Console.TableResult.DisplayType.Detect.text") + displayTypeName),
        new Separator(),
        new DisplayTypeAction(grid.isDisplayTypeApplicable(BinaryDisplayType.UUID, columnIdx), grid, columnIdx, BinaryDisplayType.UUID,
                              DataGridBundle.message("action.Console.TableResult.DisplayType.UUID.text")),
        new DisplayTypeAction(grid.isDisplayTypeApplicable(BinaryDisplayType.UUID_SWAP, columnIdx), grid, columnIdx,
                              BinaryDisplayType.UUID_SWAP,
                              DataGridBundle.message("action.Console.TableResult.DisplayType.SwappedUUID.text")),
        new DisplayTypeAction(grid.isDisplayTypeApplicable(BinaryDisplayType.TEXT, columnIdx), grid, columnIdx, BinaryDisplayType.TEXT,
                              DataGridBundle.message("action.Console.TableResult.DisplayType.Text.text")),
        new DisplayTypeAction(true, grid, columnIdx, BinaryDisplayType.HEX,
                              DataGridBundle.message("action.Console.TableResult.DisplayType.Hex.text")),
        new DisplayTypeAction(true, grid, columnIdx, BinaryDisplayType.HEX_ASCII,
                              DataGridBundle.message("action.Console.TableResult.DisplayType.Hex.ASCII.text"))
      ).toArray(AnAction.EMPTY_ARRAY);
    } else {
      return Arrays.asList(
        new DisplayTypeAction(true, grid, columnIdx, NumberDisplayType.NUMBER,
                              DataGridBundle.message("action.Console.TableResult.DisplayType.Number.text")),
        new DisplayTypeAction(true, grid, columnIdx, NumberDisplayType.TIMESTAMP_SECONDS,
                              DataGridBundle.message("action.Console.TableResult.DisplayType.Timestamp.Seconds.text")),
        new DisplayTypeAction(true, grid, columnIdx, NumberDisplayType.TIMESTAMP_MILLISECONDS,
                              DataGridBundle.message("action.Console.TableResult.DisplayType.Timestamp.Milliseconds.text")),
        new DisplayTypeAction(true, grid, columnIdx, NumberDisplayType.TIMESTAMP_MICROSECONDS,
                              DataGridBundle.message("action.Console.TableResult.DisplayType.Timestamp.Microseconds.text"))
      ).toArray(AnAction.EMPTY_ARRAY);
    }
  }

  public static class DisplayTypeAction extends ToggleAction implements DumbAware {
    private final DisplayType myType;
    private final ModelIndex<GridColumn> myColumn;
    private final DataGrid myGrid;
    private final boolean isEnabled;

    DisplayTypeAction(boolean isEnabled,
                      @NotNull DataGrid grid,
                      @NotNull ModelIndex<GridColumn> column,
                      @NotNull DisplayType type,
                      @NlsActions.ActionText @NotNull String name) {
      super(name);
      myType = type;
      myColumn = column;
      myGrid = grid;
      this.isEnabled = isEnabled;
    }

    public DisplayType getType() {
      return myType;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void update(final @NotNull AnActionEvent e) {
      super.update(e);
      e.getPresentation().setVisible(true);
      e.getPresentation().setEnabled(isEnabled);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myType == myGrid.getPureDisplayType(myColumn);
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      if (state) {
        myGrid.setDisplayType(myColumn, myType);
      }
    }
  }
}

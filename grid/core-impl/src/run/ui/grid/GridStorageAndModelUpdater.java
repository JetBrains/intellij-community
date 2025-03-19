package com.intellij.database.run.ui.grid;

import com.intellij.database.datagrid.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * @author Liudmila Kornilova
 **/
public class GridStorageAndModelUpdater implements GridModelUpdater {
  private final GridListModelBase<GridRow, GridColumn> myModel;
  private final GridMutationModel myMutationModel;
  private final MutationsStorage myStorage;

  public GridStorageAndModelUpdater(@NotNull GridListModelBase<GridRow, GridColumn> model,
                                    @NotNull GridMutationModel mutationModel,
                                    @Nullable MutationsStorage storage) {
    myModel = model;
    myMutationModel = mutationModel;
    myStorage = storage;
  }

  @Override
  public void removeRows(int firstRowIndex, int rowCount) {
    ModelIndexSet<GridRow> rows = ModelIndexSet.forRows(myMutationModel, range(firstRowIndex, rowCount));
    if (myStorage != null) {
      for (ModelIndex<GridRow> rowIdx : rows.asIterable()) {
        myStorage.clearRow(rowIdx);
      }
    }
    if (firstRowIndex < myModel.getRowCount()) {
      myModel.removeRows(firstRowIndex, Math.min(rowCount, myModel.getRowCount() - firstRowIndex));
    }
    myMutationModel.notifyRowsRemoved(rows);
  }

  @Override
  public void setColumns(@NotNull List<? extends GridColumn> columns) {
    //TODO if some of the columns are the same, leave them unchanged (it will allow to preserve layouts in ui)
    int oldColumnCount = myMutationModel.getColumnCount();
    myModel.clearColumns();
    if (myStorage != null) myStorage.clearColumns();
    myMutationModel.notifyColumnsRemoved(ModelIndexSet.forColumns(myMutationModel, range(0, oldColumnCount)));
    myModel.setColumns(columns);
    myMutationModel.notifyColumnsAdded(ModelIndexSet.forColumns(myMutationModel, range(0, myModel.getColumnCount())));
   }

  @Override
  public void setRows(int firstRowIndex, @NotNull List<? extends GridRow> rows, @NotNull GridRequestSource source) {
    int firstChangedRowIndex = -1;

    int rowsUpdated = 0;
    for (int i = firstRowIndex; i < myMutationModel.getRowCount() && rowsUpdated < rows.size(); i++, rowsUpdated++) {
      ModelIndex<GridRow> rowIdx = ModelIndex.forRow(myModel, i);
      GridRow oldRow = myMutationModel.getRow(rowIdx);
      GridRow newRow = rows.get(rowsUpdated);

      // This condition has been moved here because, in general, a GridRow may serve as a wrapper for data stored in the grid model.
      // In this scenario, performing an equality check after calling model.set(i, row) would not make much sense.
      boolean areRowsEqual = rowsEqual(Objects.requireNonNull(oldRow), newRow);

      if (myStorage != null && myStorage.isInsertedRow(rowIdx)) {
        myModel.addRow(newRow);
      }
      else {
        myModel.set(i, newRow);
      }

      if (areRowsEqual) {
        if (firstChangedRowIndex != -1) {
          ModelIndexSet<GridRow> updatedRows = ModelIndexSet.forRows(myMutationModel, range(firstChangedRowIndex, i - firstChangedRowIndex));
          ModelIndexSet<GridColumn> updatedColumns =
            ModelIndexSet.forColumns(myMutationModel, range(0, myMutationModel.getColumnCount()));
          myMutationModel.notifyCellsUpdated(updatedRows, updatedColumns, source.place);
        }
        firstChangedRowIndex = -1;
      }
      else if (firstChangedRowIndex == -1) {
        firstChangedRowIndex = i;
      }

      if (myStorage != null) myStorage.clearRow(rowIdx);
    }

    if (firstChangedRowIndex != -1) {
      ModelIndexSet<GridRow> updatedRows =
        ModelIndexSet.forRows(myMutationModel, range(firstChangedRowIndex, firstRowIndex + rowsUpdated - firstChangedRowIndex));
      ModelIndexSet<GridColumn> updatedColumns =
        ModelIndexSet.forColumns(myMutationModel, range(0, myMutationModel.getColumnCount()));
      myMutationModel.notifyCellsUpdated(updatedRows, updatedColumns, null);
    }

    int firstRow = myMutationModel.getRowCount();
    List<? extends GridRow> newRows = ContainerUtil.subList(rows, rowsUpdated);
    myModel.addRows(newRows);
    myMutationModel.notifyRowsAdded(ModelIndexSet.forRows(myMutationModel, range(firstRow, newRows.size())));
  }

  @Override
  public void addRows(List<? extends GridRow> rows) {
    int firstRow = myMutationModel.getRowCount();
    myModel.addRows(rows);
    myMutationModel.notifyRowsAdded(ModelIndexSet.forRows(myMutationModel, range(firstRow, rows.size())));
  }

  @Override
  public void afterLastRowAdded() {
    myModel.setUpdatingNow(false);
    myMutationModel.afterLastRowAdded();
  }

  private static int[] range(int first, int length) {
    return GridModelUpdaterUtil.getColumnsIndicesRange(first, length);
  }

  private static boolean rowsEqual(@NotNull GridRow row1, @NotNull GridRow row2) {
    if (row1.getSize() != row2.getSize()) return false;
    for (int i = 0; i < row1.getSize(); i++) {
      if (!Objects.equals(row1.getValue(i), row2.getValue(i))) return false;
    }
    return true;
  }
}

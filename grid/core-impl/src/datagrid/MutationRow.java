package com.intellij.database.datagrid;

import com.intellij.database.run.ui.grid.GridMutationModel;
import org.jetbrains.annotations.NotNull;

public class MutationRow extends DataConsumer.Row {
  private final GridMutator.DatabaseMutator<GridRow, GridColumn> myMutator;
  private final GridModel<GridRow, GridColumn> myModel;


  public MutationRow(@NotNull ModelIndex<GridRow> rowIdx,
                     Object @NotNull [] initialData,
                     @NotNull GridMutator.DatabaseMutator<GridRow, GridColumn> mutator,
                     @NotNull GridModel<GridRow, GridColumn> databaseModel) {
    super(rowIdx.asInteger() + 1, initialData);
    myMutator = mutator;
    myModel = databaseModel;
  }

  @Override
  public Object getValue(int columnNum) {
    ModelIndex<GridRow> row = ModelIndex.forRow(myModel, GridRow.toRealIdx(this));
    ModelIndex<GridColumn> column = ModelIndex.forColumn(myModel, columnNum);
    return GridMutationModel.getValueAt(row, column, myMutator, myModel);
  }
}

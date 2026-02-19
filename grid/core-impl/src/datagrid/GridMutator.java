package com.intellij.database.datagrid;

import com.intellij.database.datagrid.mutating.CellMutation;
import com.intellij.database.datagrid.mutating.MutationData;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface GridMutator<Row, Column> {

  boolean isUpdateSafe(@NotNull ModelIndexSet<Row> rowIndices, @NotNull ModelIndexSet<Column> columnIndices, @Nullable Object newValue);

  boolean hasPendingChanges();

  boolean hasUnparsedValues();

  boolean isUpdateImmediately();

  void mutate(@NotNull GridRequestSource source,
              @NotNull ModelIndexSet<Row> row,
              @NotNull ModelIndexSet<Column> column,
              @Nullable Object newValue,
              boolean allowImmediateUpdate);

  void mutate(@NotNull GridRequestSource source,
              @NotNull List<CellMutation> mutations,
              boolean allowImmediateUpdate);

  interface RowsMutator<Row, Column> extends GridMutator<Row, Column> {

    void deleteRows(@NotNull GridRequestSource source, @NotNull ModelIndexSet<Row> rows);

    void insertRows(@NotNull GridRequestSource source, int amount);

    void cloneRow(@NotNull GridRequestSource source, @NotNull ModelIndex<Row> toClone);

    boolean isDeletedRow(@NotNull ModelIndex<Row> row);

    boolean isDeletedRows(@NotNull ModelIndexSet<Row> rows);

    boolean isInsertedRow(@NotNull ModelIndex<Row> row);

    int getInsertedRowsCount();

    @Nullable
    ModelIndex<Row> getLastInsertedRow();

    @NotNull
    ModelIndexSet<Row> getAffectedRows();

    @NotNull
    JBIterable<ModelIndex<Row>> getInsertedRows();
  }

  interface DatabaseMutator<Row, Column> extends RowsMutator<Row, Column>, ColumnsMutator<Row, Column> {
    void submit(@NotNull GridRequestSource source, boolean includeInserted);

    @NotNull
    String getPendingChanges();

    @Nullable
    MutationType getMutationType(@NotNull ModelIndex<Row> row);

    boolean hasUnparsedValues(@NotNull ModelIndex<Row> row);

    @Nullable
    MutationData getMutation(@NotNull ModelIndex<Row> row, @NotNull ModelIndex<Column> column);

    @Nullable
    MutationType getMutationType(@NotNull ModelIndex<Row> row, @NotNull ModelIndex<Column> column);

    boolean isFailed();

    boolean hasMutatedRows(@NotNull ModelIndexSet<Row> rows, @NotNull ModelIndexSet<Column> columns);

    void revert(@NotNull GridRequestSource source,
                @NotNull ModelIndexSet<Row> rows,
                @NotNull ModelIndexSet<Column> columns);
  }


  interface ColumnsMutator<Row, Column> extends GridMutator<Row, Column> {

    void deleteColumns(@NotNull GridRequestSource source, @NotNull ModelIndexSet<Column> columns);

    void insertColumn(@NotNull GridRequestSource source, @Nullable String name);

    default void moveColumn(
      @NotNull GridRequestSource source,
      @NotNull ModelIndex<Column> from,
      @NotNull ModelIndex<Column> to
    ) {}

    void cloneColumn(@NotNull GridRequestSource source, @NotNull ModelIndex<Column> toClone);

    int getInsertedColumnsCount();

    @NotNull JBIterable<ModelIndex<Column>> getInsertedColumns();

    boolean isInsertedColumn(@NotNull ModelIndex<Column> idx);

    boolean isDeletedColumn(@NotNull ModelIndex<Column> idx);

    @Nullable
    Column getInsertedColumn(@NotNull ModelIndex<Column> idx);

    void renameColumn(@NotNull GridRequestSource source, @NotNull ModelIndex<Column> idx, @NotNull String newName);
  }
}

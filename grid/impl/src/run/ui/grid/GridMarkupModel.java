package com.intellij.database.run.ui.grid;

import com.intellij.database.datagrid.ModelIndex;
import com.intellij.database.datagrid.ModelIndexSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface GridMarkupModel<Row, Column> {
  @Nullable
  CellAttributes getCellAttributes(@NotNull ModelIndex<Row> row, @NotNull ModelIndex<Column> column, @NotNull GridColorsScheme scheme);

  @Nullable
  CellAttributes getRowHeaderAttributes(@NotNull ModelIndex<Row> row, @NotNull GridColorsScheme scheme);

  @Nullable
  CellAttributes getColumnHeaderAttributes(@NotNull ModelIndex<Column> column, @NotNull GridColorsScheme scheme);

  @NotNull
  CellHighlighting highlightCells(@NotNull ModelIndexSet<Row> rows,
                                  @NotNull ModelIndexSet<Column> columns,
                                  @NotNull CellAttributesKey attributes,
                                  int level);

  void removeHighlighting(@NotNull Highlighting highlighting);

  void removeAllHighlightings(@NotNull Collection<Highlighting> highlighting);

  void removeCellHighlighting(@NotNull CellHighlighting highlighting);

  void removeAllCellHighlightings(@NotNull Collection<CellHighlighting> highlighting);

  @NotNull
  HeaderHighlighting<Row> highlightRowHeaders(@NotNull ModelIndexSet<Row> rows, @NotNull CellAttributesKey attributes, int level);

  void removeRowHeaderHighlighting(@NotNull HeaderHighlighting<Row> highlighting);

  void removeAllRowHeaderHighlightings(@NotNull Collection<HeaderHighlighting<Row>> highlightings);

  @NotNull
  HeaderHighlighting<Column> highlightColumnHeaders(@NotNull ModelIndexSet<Column> columns, @NotNull CellAttributesKey attributes, int level);

  void removeColumnHeaderHighlighting(@NotNull HeaderHighlighting<Column> highlighting);

  void removeAllColumnHeaderHighlightings(@NotNull Collection<HeaderHighlighting<Column>> highlightings);

  void clear();

  interface Highlighting {
    @NotNull
    CellAttributesKey getAttributes();

    int getLevel();
  }

  interface CellHighlighting extends Highlighting {
    boolean contains(int row, int column);
  }

  interface HeaderHighlighting<S> extends Highlighting {
    boolean contains(int rowOrCol);
  }
}

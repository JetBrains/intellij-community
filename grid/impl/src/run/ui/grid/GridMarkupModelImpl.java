package com.intellij.database.run.ui.grid;

import com.intellij.database.datagrid.ModelIndex;
import com.intellij.database.datagrid.ModelIndexSet;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public class GridMarkupModelImpl<GridRow, GridColumn> implements GridMarkupModel<GridRow, GridColumn> {
  private final List<CellHighlighting> myCellHighlightings;
  private final List<HeaderHighlighting<GridRow>> myRowHeaderHighlightings;
  private final List<HeaderHighlighting<GridColumn>> myColumnHeaderHighlightings;

  private static final Comparator<Highlighting> highlightingComparator = (o1, o2) -> {
    if (o1 == null || o2 == null) {
      return o1 == null && o2 == null ? 0 : (o1 == null ? -1 : 1);
    }
    return Integer.compare(o1.getLevel(), o2.getLevel());
  };

  public GridMarkupModelImpl() {
    myCellHighlightings = new ArrayList<>();
    myRowHeaderHighlightings = new ArrayList<>();
    myColumnHeaderHighlightings = new ArrayList<>();
  }

  @Override
  public @Nullable CellAttributes getCellAttributes(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column, @NotNull GridColorsScheme scheme) {
    synchronized (myCellHighlightings) {
      CellAttributes res = null;
      List<Highlighting> tmp = new ArrayList<>();
      for (CellHighlighting highlighting : myCellHighlightings) {
        if (highlighting.contains(row.asInteger(), column.asInteger())) {
          tmp.add(highlighting);
        }
      }
      tmp.sort(highlightingComparator);
      for(Highlighting highlighting : tmp) {
        res = CellAttributes.merge(res, scheme.getAttributes(highlighting.getAttributes()));
      }
      return res;
    }
  }

  @Override
  public @Nullable CellAttributes getRowHeaderAttributes(@NotNull ModelIndex<GridRow> row, @NotNull GridColorsScheme scheme) {
    synchronized (myRowHeaderHighlightings) {
      CellAttributes res = null;
      List<Highlighting> tmp = new ArrayList<>();
      for (HeaderHighlighting<GridRow> highlighting : myRowHeaderHighlightings) {
        if (highlighting.contains(row.asInteger())) {
          tmp.add(highlighting);
        }
      }
      tmp.sort(highlightingComparator);
      for(Highlighting highlighting : tmp) {
        res = CellAttributes.merge(res, scheme.getAttributes(highlighting.getAttributes()));
      }
      return res;
    }
  }

  @Override
  public @NotNull CellHighlighting highlightCells(@NotNull ModelIndexSet<GridRow> rows,
                                                  @NotNull ModelIndexSet<GridColumn> columns,
                                                  @NotNull CellAttributesKey attributes,
                                                  int level) {
    synchronized (myCellHighlightings) {
      CellHighlighting res = new CellHighlightingImpl(rows, columns, attributes, level);
      myCellHighlightings.add(res);
      return res;
    }
  }

  @Override
  public void removeCellHighlighting(@NotNull CellHighlighting highlighting) {
    synchronized (myCellHighlightings) {
      myCellHighlightings.remove(highlighting);
    }
  }

  @Override
  public void removeAllCellHighlightings(@NotNull Collection<CellHighlighting> highlightings) {
    synchronized (myCellHighlightings) {
      myCellHighlightings.removeAll(highlightings);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public void removeHighlighting(@NotNull Highlighting highlighting) {
    if (highlighting instanceof CellHighlighting) {
      removeCellHighlighting((CellHighlighting)highlighting);
    }
    else if (highlighting instanceof HeaderHighlighting) {
      removeRowHeaderHighlighting((HeaderHighlighting)highlighting);
      removeColumnHeaderHighlighting((HeaderHighlighting)highlighting);
    }
  }

  @Override
  public void removeAllHighlightings(@NotNull Collection<Highlighting> highlightings) {
    for (Highlighting highlighting : highlightings) {
      removeHighlighting(highlighting);
    }
  }


  @Override
  public @NotNull HeaderHighlighting<GridRow> highlightRowHeaders(@NotNull ModelIndexSet<GridRow> rows, @NotNull CellAttributesKey attributes, int level) {
    synchronized (myRowHeaderHighlightings) {
      HeaderHighlighting<GridRow> res = new HeaderHighlightingImpl<>(rows, attributes, level);
      myRowHeaderHighlightings.add(res);
      return res;
    }
  }

  @Override
  public void removeRowHeaderHighlighting(@NotNull HeaderHighlighting<GridRow> highlighting) {
    synchronized (myRowHeaderHighlightings) {
      myRowHeaderHighlightings.remove(highlighting);
    }
  }

  @Override
  public void removeAllRowHeaderHighlightings(@NotNull Collection<HeaderHighlighting<GridRow>> highlightings) {
    synchronized (myRowHeaderHighlightings) {
      myRowHeaderHighlightings.removeAll(highlightings);
    }
  }

  @Override
  public @Nullable CellAttributes getColumnHeaderAttributes(@NotNull ModelIndex<GridColumn> column, @NotNull GridColorsScheme scheme) {
    synchronized (myColumnHeaderHighlightings) {
      CellAttributes res = null;
      List<Highlighting> tmp = new ArrayList<>();
      for (HeaderHighlighting<GridColumn> highlighting : myColumnHeaderHighlightings) {
        if (highlighting.contains(column.asInteger())) {
          tmp.add(highlighting);
        }
      }
      tmp.sort(highlightingComparator);
      for(Highlighting highlighting : tmp) {
        res = CellAttributes.merge(res, scheme.getAttributes(highlighting.getAttributes()));
      }
      return res;
    }
  }

  @Override
  public @NotNull HeaderHighlighting<GridColumn> highlightColumnHeaders(@NotNull ModelIndexSet<GridColumn> columns,
                                                                        @NotNull CellAttributesKey attributes,
                                                                        int level) {
    synchronized (myColumnHeaderHighlightings) {
      HeaderHighlighting<GridColumn> res = new HeaderHighlightingImpl<>(columns, attributes, level);
      myColumnHeaderHighlightings.add(res);
      return res;
    }
  }

  @Override
  public void removeColumnHeaderHighlighting(@NotNull HeaderHighlighting<GridColumn> highlighting) {
    synchronized (myColumnHeaderHighlightings) {
      myColumnHeaderHighlightings.remove(highlighting);
    }
  }

  @Override
  public void removeAllColumnHeaderHighlightings(@NotNull Collection<HeaderHighlighting<GridColumn>> highlightings) {
    synchronized (myColumnHeaderHighlightings) {
      myColumnHeaderHighlightings.removeAll(highlightings);
    }
  }

  @Override
  public void clear() {
    synchronized (myCellHighlightings) {
      myCellHighlightings.clear();
    }
    synchronized (myRowHeaderHighlightings) {
      myRowHeaderHighlightings.clear();
    }
    synchronized (myColumnHeaderHighlightings) {
      myColumnHeaderHighlightings.clear();
    }
  }

  private abstract static class AbstractHighlighting implements Highlighting {
    private AbstractHighlighting(CellAttributesKey attributes, int level) {
      myAttributes = attributes;
      myLevel = level;
    }

    @Override
    public @NotNull CellAttributesKey getAttributes() {
      return myAttributes;
    }

    @Override
    public int getLevel() {
      return myLevel;
    }

    private final CellAttributesKey myAttributes;
    private final int myLevel;
  }

  private static final class CellHighlightingImpl extends AbstractHighlighting implements CellHighlighting {
    private <GridRow, GridColumn> CellHighlightingImpl(ModelIndexSet<GridRow> rows,
                                               ModelIndexSet<GridColumn> columns,
                                               CellAttributesKey attributes,
                                               int level) {
      super(attributes, level);
      myColumns = new IntOpenHashSet(columns.asArray());
      myRows = new IntOpenHashSet(rows.asArray());
    }

    @Override
    public boolean contains(int row, int column) {
      return myColumns.contains(column) && myRows.contains(row);
    }

    private final IntSet myColumns, myRows;
  }

  private static final class HeaderHighlightingImpl<S> extends AbstractHighlighting implements HeaderHighlighting<S> {
    private final IntSet myRowOrCols;

    private HeaderHighlightingImpl(ModelIndexSet<S> rows, CellAttributesKey attributes, int level) {
      super(attributes, level);
      myRowOrCols = new IntOpenHashSet(rows.asArray());
    }

    @Override
    public boolean contains(int rowOrCol) {
      return myRowOrCols.contains(rowOrCol);
    }
  }
}

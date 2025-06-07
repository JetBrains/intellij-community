package com.intellij.database.data.types;

import com.intellij.database.datagrid.*;
import com.intellij.database.datagrid.mutating.CellMutation;
import com.intellij.database.remote.jdbc.LobInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BaseDataTypeConversion implements DataTypeConversion {
  protected final GridColumn myFirstColumn;
  protected final Object myObject;

  protected final CoreGrid<GridRow, GridColumn> mySecondGrid;
  protected final ModelIndex<GridColumn> mySecondColumnIdx;
  protected final ModelIndex<GridRow> mySecondRowIdx;


  public BaseDataTypeConversion(@NotNull GridColumn firstColumn,
                                @NotNull ModelIndex<GridColumn> secondColIdx,
                                @NotNull ModelIndex<GridRow> secondRowIdx,
                                @NotNull CoreGrid<GridRow, GridColumn> grid,
                                @Nullable Object object) {
    myFirstColumn = firstColumn;
    myObject = object;
    mySecondColumnIdx = secondColIdx;
    mySecondRowIdx = secondRowIdx;
    mySecondGrid = grid;
  }

  @Override
  public @NotNull CellMutation.Builder convert(@NotNull ConversionGraph graph) {
    return build();
  }

  @Override
  public @NotNull CellMutation.Builder build() {
    CellMutation.Builder builder = new CellMutation.Builder()
      .row(mySecondRowIdx)
      .column(mySecondColumnIdx)
      .value(null);
    return builder.value(myObject);
  }

  @Override
  public boolean isValid() {
    return mySecondColumnIdx.isValid(mySecondGrid) && mySecondRowIdx.isValid(mySecondGrid);
  }

  public static class Builder implements DataTypeConversion.Builder {
    protected GridColumn myFirstColumn;
    protected int myFirstRowIdx;
    protected int myFirstColumnIdx;
    protected Object myObject;

    protected CoreGrid<GridRow, GridColumn> mySecondGrid;
    protected ModelIndex<GridColumn> mySecondColumnIdx;
    protected ModelIndex<GridRow> mySecondRowIdx;

    public Builder() {
    }

    public Builder(@Nullable GridColumn firstColumn,
                    int firstRowIdx,
                    int firstColumnIdx,
                    @Nullable Object object,
                    @Nullable CoreGrid<GridRow, GridColumn> secondGrid,
                    @Nullable ModelIndex<GridColumn> secondColumnIdx,
                    @Nullable ModelIndex<GridRow> secondRowIdx) {
      myFirstColumn = firstColumn;
      myFirstRowIdx = firstRowIdx;
      myFirstColumnIdx = firstColumnIdx;
      myObject = object;
      mySecondGrid = secondGrid;
      mySecondColumnIdx = secondColumnIdx;
      mySecondRowIdx = secondRowIdx;
    }

    @Override
    public @NotNull Builder firstColumn(@NotNull GridColumn column) {
      myFirstColumn = column;
      return this;
    }

    @Override
    public @NotNull Builder firstRowIdx(int rowIdx) {
      myFirstRowIdx = rowIdx;
      return this;
    }

    @Override
    public int firstRowIdx() {
      return myFirstRowIdx;
    }

    @Override
    public @NotNull Builder firstColumnIdx(int columnIdx) {
      myFirstColumnIdx = columnIdx;
      return this;
    }

    @Override
    public int firstColumnIdx() {
      return myFirstColumnIdx;
    }

    @Override
    public @NotNull Builder value(@Nullable Object value) {
      myObject = value;
      return this;
    }

    @Override
    public int size() {
      if (myObject instanceof String v) {
        return v.length();
      }
      if (myObject instanceof byte[] v) {
        return v.length;
      }
      if (myObject instanceof LobInfo<?> v) {
        return (int)Math.min(v.getLoadedDataLength(), Integer.MAX_VALUE);
      }
      return 1000; // 1000 is default size from CopyPasteManagerWithHistory.getSize
    }

    @Override
    public @NotNull Builder secondGrid(@NotNull CoreGrid<GridRow, GridColumn> grid) {
      mySecondGrid = grid;
      return this;
    }

    public Builder secondColumnIndex(@NotNull ModelIndex<GridColumn> idx) {
      mySecondColumnIdx = idx;
      return this;
    }

    @SuppressWarnings("UnusedReturnValue")
    public @NotNull Builder secondRowIdx(@NotNull ModelIndex<GridRow> idx) {
      mySecondRowIdx = idx;
      return this;
    }

    @Override
    public @NotNull Builder offset(int rows, int columns) {
      secondRowIdx(ViewIndex.forRow(mySecondGrid, myFirstRowIdx + rows).toModel(mySecondGrid));
      return secondColumnIndex(
        ViewIndex.forColumn(mySecondGrid, myFirstColumnIdx + columns).toModel(mySecondGrid)
      );
    }

    @Override
    public @NotNull Builder firstGrid(@Nullable CoreGrid<GridRow, GridColumn> grid) {
      return this;
    }

    @Override
    public @NotNull Builder copy() {
      return new Builder(
        myFirstColumn,
        myFirstRowIdx,
        myFirstColumnIdx,
        myObject,
        mySecondGrid,
        mySecondColumnIdx,
        mySecondRowIdx
      );
    }

    @Override
    public @NotNull DataTypeConversion build() {
      return new BaseDataTypeConversion(
        myFirstColumn,
        mySecondColumnIdx,
        mySecondRowIdx,
        mySecondGrid,
        myObject
      );
    }
  }
}

package com.intellij.database.datagrid.mutating;

import com.intellij.database.datagrid.GridRow;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public class RowMutation implements DatabaseMutation {
  private final GridRow myRow;
  private final @Unmodifiable List<ColumnQueryData> myData;

  public RowMutation(@NotNull GridRow row, @NotNull List<ColumnQueryData> data) {
    myRow = row;
    myData = ContainerUtil.sorted(data);
  }

  public @NotNull RowMutation merge(@Nullable RowMutation mutation) {
    return mutation == null ? this : new RowMutation(myRow, ContainerUtil.concat(myData, mutation.myData));
  }

  public @NotNull @Unmodifiable List<ColumnQueryData> getData() {
    return myData;
  }

  public @NotNull GridRow getRow() {
    return myRow;
  }

  @Override
  public int compareTo(@NotNull DatabaseMutation o) {
    return o instanceof RowMutation ? Integer.compare(myRow.getRowNum(), ((RowMutation)o).myRow.getRowNum()) : -1;
  }
}

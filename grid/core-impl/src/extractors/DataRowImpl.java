package com.intellij.database.extractors;

import com.intellij.database.datagrid.GridRow;
import com.intellij.database.extensions.DataColumn;
import com.intellij.database.extensions.DataRow;
import com.intellij.database.run.ReservedCellValue;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DataRowImpl implements DataRow {
  private final GridRow myRow;
  private final boolean myFirst;
  private final boolean myLast;

  public DataRowImpl(@NotNull GridRow row, boolean first, boolean last) {
    myRow = row;
    myFirst = first;
    myLast = last;
  }

  @Override
  public int rowNumber() {
    return myRow.getRowNum();
  }

  @Override
  public boolean first() {
    return myFirst;
  }

  @Override
  public boolean last() {
    return myLast;
  }

  @Override
  public @NotNull List<Object> data() {
    return ContainerUtil.newArrayList(myRow);
  }

  @Override
  public @Nullable Object value(@NotNull DataColumn column) {

    return ((DataColumnImpl)column).getValue(myRow);
  }

  @Override
  public boolean hasValue(@NotNull DataColumn column) {
    return ((DataColumnImpl)column).getValue(myRow) != ReservedCellValue.UNSET;
  }

  @NotNull
  GridRow getRow() {
    return myRow;
  }
}

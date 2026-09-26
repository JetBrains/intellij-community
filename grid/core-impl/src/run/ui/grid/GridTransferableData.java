package com.intellij.database.run.ui.grid;

import com.intellij.database.data.types.DataTypeConversion;
import com.intellij.openapi.ide.Sizeable;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.List;

public class GridTransferableData implements Transferable, Sizeable {
  public static final DataFlavor ourFlavor = new DataFlavor(GridTransferableData.class, "Cells");

  private final List<DataTypeConversion.Builder> myConversions;
  private final Transferable myTransferable;
  private final int myFirstRowIdx;
  private final int myFirstColumnIdx;
  private final int myRowsCount;

  public GridTransferableData(@NotNull List<DataTypeConversion.Builder> conversions,
                              @NotNull Transferable transferable,
                              int firstRowIdx,
                              int firstColumnIdx,
                              int rowsCount) {
    myConversions = conversions;
    myTransferable = transferable;
    myFirstRowIdx = firstRowIdx;
    myFirstColumnIdx = firstColumnIdx;
    myRowsCount = rowsCount;
  }

  @Override
  public DataFlavor[] getTransferDataFlavors() {
    return ArrayUtil.mergeArrays(ContainerUtil.ar(ourFlavor), myTransferable.getTransferDataFlavors());
  }

  @Override
  public boolean isDataFlavorSupported(DataFlavor flavor) {
    return flavor == ourFlavor || myTransferable.isDataFlavorSupported(flavor);
  }

  @Override
  public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
    return flavor == ourFlavor ? this : myTransferable.getTransferData(flavor);
  }

  public @NotNull List<DataTypeConversion.Builder> getConversions() {
    return myConversions;
  }

  public int getFirstRowIdx() {
    return myFirstRowIdx;
  }

  public int getRowsCount() {
    return myRowsCount;
  }

  public int getColumnsCount() {
    return myConversions.size() / myRowsCount;
  }

  public int getFirstColumnIdx() {
    return myFirstColumnIdx;
  }

  public @NotNull Transferable getTransferable() {
    return myTransferable;
  }

  @Override
  public @Range(from = 0, to = Integer.MAX_VALUE) int getSize() {
    int size = 0;
    for (DataTypeConversion.Builder conversion : myConversions) {
      size += Math.max(100, conversion.size());
    }
    return size;
  }
}

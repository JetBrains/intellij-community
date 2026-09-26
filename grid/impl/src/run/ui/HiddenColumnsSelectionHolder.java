package com.intellij.database.run.ui;

import com.intellij.database.datagrid.DataGrid;
import org.jetbrains.annotations.NotNull;

import java.util.BitSet;
import java.util.stream.IntStream;

public class HiddenColumnsSelectionHolder {
  private final BitSet mySelectedHiddenColumns;
  private boolean myWholeRowSelected;
  private boolean myIgnoreChanges;

  public HiddenColumnsSelectionHolder() {
    mySelectedHiddenColumns = new BitSet();
  }

  private HiddenColumnsSelectionHolder(HiddenColumnsSelectionHolder copy) {
    mySelectedHiddenColumns = BitSet.valueOf(copy.mySelectedHiddenColumns.toLongArray());
    myWholeRowSelected = copy.myWholeRowSelected;
    myIgnoreChanges = copy.myIgnoreChanges;
  }

  public void setWholeRowSelected(boolean selected) {
    if (myIgnoreChanges) return;
    myWholeRowSelected = selected;
  }

  public void columnHidden(int modelIndex) {
    mySelectedHiddenColumns.set(modelIndex);
  }

  public void columnShown(int modelIndex) {
    mySelectedHiddenColumns.clear(modelIndex);
  }

  public void startAdjusting() {
    myIgnoreChanges = true;
  }

  public void endAdjusting() {
    myIgnoreChanges = false;
  }

  public boolean contains(int modelIndex) {
    return myWholeRowSelected || mySelectedHiddenColumns.get(modelIndex);
  }

  public HiddenColumnsSelectionHolder copy() {
    return new HiddenColumnsSelectionHolder(this);
  }

  public int[] selectedModelIndices(@NotNull DataGrid panel) {
    return myWholeRowSelected
           ? IntStream.range(0, panel.getDataModel(DataAccessType.DATABASE_DATA).getColumnCount()).toArray()
           : mySelectedHiddenColumns.stream().toArray();
  }

  public void reset() {
    mySelectedHiddenColumns.clear();
    myWholeRowSelected = false;
    myIgnoreChanges = false;
  }
}

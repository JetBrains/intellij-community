// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.core;

final class HorizontalInfo extends DimensionInfo{
  HorizontalInfo(final LayoutState layoutState, final int gap){
    super(layoutState, gap);
  }

  @Override
  protected int getOriginalCell(final GridConstraints constraints){
    return constraints.getColumn();
  }

  @Override
  protected int getOriginalSpan(final GridConstraints constraints){
    return constraints.getColSpan();
  }

  @Override
  int getSizePolicy(final int componentIndex){
    return myLayoutState.getConstraints(componentIndex).getHSizePolicy();
  }

  @Override
  int getChildLayoutCellCount(final GridLayoutManager childLayout) {
    return childLayout.getColumnCount();
  }

  @Override
  public int getMinimumWidth(final int componentIndex){
    return getMinimumSize(componentIndex).width;
  }

  @Override
  public DimensionInfo getDimensionInfo(GridLayoutManager grid) {
    return grid.getHorizontalInfo();
  }

  @Override
  public int getCellCount(){
    return myLayoutState.getColumnCount();
  }

  @Override
  public int getPreferredWidth(final int componentIndex){
    return getPreferredSize(componentIndex).width;
  }
}

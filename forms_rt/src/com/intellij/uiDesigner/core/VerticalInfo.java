/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.uiDesigner.core;

final class VerticalInfo extends DimensionInfo {
  public VerticalInfo(final LayoutState layoutState, final int gap){
    super(layoutState, gap);
  }

  protected int getOriginalCell(final GridConstraints constraints){
    return constraints.getRow();
  }

  protected int getOriginalSpan(final GridConstraints constraints){
    return constraints.getRowSpan();
  }

  int getSizePolicy(final int componentIndex){
    return myLayoutState.getConstraints(componentIndex).getVSizePolicy();
  }

  public int getMinimumWidth(final int componentIndex){
    return getMinimumSize(componentIndex).height;
  }

  public int getCellCount(){
    return myLayoutState.getRowCount();
  }

  public int getPreferredWidth(final int componentIndex){
    return getPreferredSize(componentIndex).height;
  }
}

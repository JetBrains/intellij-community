/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.uiDesigner.core;

final class HorizontalInfo extends DimensionInfo{
  public HorizontalInfo(final LayoutState layoutState, final int gap){
    super(layoutState, gap);
  }

  protected int getOriginalCell(final GridConstraints constraints){
    return constraints.getColumn();
  }

  protected int getOriginalSpan(final GridConstraints constraints){
    return constraints.getColSpan();
  }

  int getSizePolicy(final int componentIndex){
    return myLayoutState.getConstraints(componentIndex).getHSizePolicy();
  }

  public int getMinimumWidth(final int componentIndex){
    return getMinimumSize(componentIndex).width;
  }

  public int getCellCount(){
    return myLayoutState.getColumnCount();
  }

  public int getPreferredWidth(final int componentIndex){
    return getPreferredSize(componentIndex).width;
  }
}

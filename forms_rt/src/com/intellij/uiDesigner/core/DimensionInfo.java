/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.uiDesigner.core;

import java.awt.*;
import java.util.ArrayList;

/**
 * public for test purposes
 */ 
public abstract class DimensionInfo {
  private final int[] myCell;
  private final int[] mySpan;
  protected final LayoutState myLayoutState;
  private final int[] myStretches;
  private final int[] mySpansAfterElimination;
  private final int[] myCellSizePolicies;
  private final int myGap;

  public DimensionInfo(final LayoutState layoutState, final int gap){
    if (layoutState == null){
      throw new IllegalArgumentException("layoutState cannot be null");
    }
    if (gap < 0){
      throw new IllegalArgumentException("invalid gap: " + gap);
    }
    myLayoutState = layoutState;
    myGap = gap;

    myCell = new int[layoutState.getComponentCount()];
    mySpan = new int[layoutState.getComponentCount()];
    
    for (int i = 0; i < layoutState.getComponentCount(); i++) {
      final GridConstraints c = layoutState.getConstraints(i);
      myCell[i] = getOriginalCell(c);
      mySpan[i] = getOriginalSpan(c);
    }

    myStretches = new int[getCellCount()];
    for (int i = 0; i < myStretches.length; i++) {
      myStretches[i] = 1;
    }
    //TODO[anton,vova] handle stretches
    
    final ArrayList elimitated = new ArrayList();
    mySpansAfterElimination = (int[])mySpan.clone();
    Util.eliminate((int[])myCell.clone(), mySpansAfterElimination, elimitated);
    
    myCellSizePolicies = new int[getCellCount()];
    for (int i = 0; i < myCellSizePolicies.length; i++) {
      myCellSizePolicies[i] = getCellSizePolicyImpl(i, elimitated);
    }
  }

  public final int getComponentCount(){
    return myLayoutState.getComponentCount();
  }

  public final Component getComponent(final int componentIndex){
    return myLayoutState.getComponent(componentIndex);
  }

  public abstract int getCellCount();

  public abstract int getPreferredWidth(int componentIndex);
  public abstract int getMinimumWidth(int componentIndex);

  public final int getCell(final int componentIndex){
    return myCell[componentIndex];
  }
  
  public final int getSpan(final int componentIndex){
    return mySpan[componentIndex];
  }

  public final int getStretch(final int cellIndex){
    return myStretches[cellIndex];
  }

  protected abstract int getOriginalCell(GridConstraints constraints);
  protected abstract int getOriginalSpan(GridConstraints constraints);
  
  abstract int getSizePolicy(int componentIndex);

  public final int getGap(){
    return myGap;
  }

  private boolean componentBelongsCell(final int componentIndex, final int cellIndex) {
    final int componentStartCell = getCell(componentIndex);
    final int span = getSpan(componentIndex);
    return componentStartCell <= cellIndex && cellIndex < componentStartCell + span;
  }
  
  public final int getCellSizePolicy(final int cellIndex){
    return myCellSizePolicies[cellIndex];
  }
  
  private int getCellSizePolicyImpl(final int cellIndex, final ArrayList eliminatedCells){
    for (int i = eliminatedCells.size() - 1; i >= 0; i--) {
      if (cellIndex == ((Integer)eliminatedCells.get(i)).intValue()) {
        return GridConstraints.SIZEPOLICY_CAN_SHRINK;
      }
    }
    
    boolean canShrink = true;
    boolean canGrow = false;
    boolean wantGrow = false;

    boolean weakCanGrow = true;
    boolean weakWantGrow = true;
    
    int countOfBelongingComponents = 0;
    
    for (int i = 0; i < getComponentCount(); i++) {
      if (!componentBelongsCell(i, cellIndex)){
        continue;
      }
      
      countOfBelongingComponents++;
      
      final int p = getSizePolicy(i);

      final boolean thisCanShrink = (p & GridConstraints.SIZEPOLICY_CAN_SHRINK) != 0;
      final boolean thisCanGrow = (p & GridConstraints.SIZEPOLICY_CAN_GROW) != 0;
      final boolean thisWantGrow = (p & GridConstraints.SIZEPOLICY_WANT_GROW) != 0;
      
      if (getCell(i) == cellIndex && mySpansAfterElimination[i] == 1) {
        canShrink &= thisCanShrink;
        canGrow |= thisCanGrow;
        wantGrow |= thisWantGrow;
      }

      if (!thisCanGrow) {
        weakCanGrow = false;
      }
      if (!thisWantGrow) {
        weakWantGrow = false;
      }
    }

    return
      (canShrink ? GridConstraints.SIZEPOLICY_CAN_SHRINK : 0) |
      (canGrow || (countOfBelongingComponents > 0 && weakCanGrow) ? GridConstraints.SIZEPOLICY_CAN_GROW : 0) |
      (wantGrow || (countOfBelongingComponents > 0 && weakWantGrow) ? GridConstraints.SIZEPOLICY_WANT_GROW : 0);
  }

  protected final Dimension getPreferredSize(final int componentIndex){
    Dimension size = myLayoutState.myPreferredSizes[componentIndex];
    if (size == null) {
      size = Util.getPreferredSize(myLayoutState.getComponent(componentIndex), myLayoutState.getConstraints(componentIndex));
      myLayoutState.myPreferredSizes[componentIndex] = size;
    }
    return size;
  }

  protected final Dimension getMinimumSize(final int componentIndex){
    Dimension size = myLayoutState.myMinimumSizes[componentIndex];
    if (size == null) {
      size = Util.getMinimumSize(myLayoutState.getComponent(componentIndex), myLayoutState.getConstraints(componentIndex));
      myLayoutState.myMinimumSizes[componentIndex] = size;
    }
    return size;
  }

}

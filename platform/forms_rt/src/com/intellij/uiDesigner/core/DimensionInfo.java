/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.uiDesigner.core;

import java.awt.*;
import java.util.ArrayList;

/**
 * public for test purposes
 * @noinspection AbstractMethodCallInConstructor,RedundantCast
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

  public final GridConstraints getConstraints(int componentIndex) {
    return myLayoutState.getConstraints(componentIndex);
  }

  public abstract int getCellCount();

  public abstract int getPreferredWidth(int componentIndex);
  public abstract int getMinimumWidth(int componentIndex);
  public abstract DimensionInfo getDimensionInfo(GridLayoutManager grid);

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

  abstract int getChildLayoutCellCount(final GridLayoutManager childLayout);

  public final int getGap(){
    return myGap;
  }

  public boolean componentBelongsCell(final int componentIndex, final int cellIndex) {
    final int componentStartCell = getCell(componentIndex);
    final int span = getSpan(componentIndex);
    return componentStartCell <= cellIndex && cellIndex < componentStartCell + span;
  }
  
  public final int getCellSizePolicy(final int cellIndex){
    return myCellSizePolicies[cellIndex];
  }
  
  private int getCellSizePolicyImpl(final int cellIndex, final ArrayList eliminatedCells){
    int policyFromChild = getCellSizePolicyFromInheriting(cellIndex);
    if (policyFromChild != -1) {
      return policyFromChild;
    }
    for (int i = eliminatedCells.size() - 1; i >= 0; i--) {
      if (cellIndex == ((Integer)eliminatedCells.get(i)).intValue()) {
        return GridConstraints.SIZEPOLICY_CAN_SHRINK;
      }
    }

    return calcCellSizePolicy(cellIndex);
  }

  private int calcCellSizePolicy(final int cellIndex) {
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

  private int getCellSizePolicyFromInheriting(final int cellIndex) {
    int nonInheritingComponentsInCell = 0;
    int policyFromInheriting = -1;
    for(int i=getComponentCount() - 1; i >= 0; i--) {
      if (!componentBelongsCell(i, cellIndex)) {
        continue;
      }
      Component child = getComponent(i);
      GridConstraints c = getConstraints(i);
      Container container = findAlignedChild(child, c);
      if (container != null) {
        GridLayoutManager grid = (GridLayoutManager) container.getLayout();
        grid.validateInfos(container);
        DimensionInfo info = getDimensionInfo(grid);
        final int policy = info.calcCellSizePolicy(cellIndex - getOriginalCell(c));
        if (policyFromInheriting == -1) {
          policyFromInheriting = policy;
        }
        else {
          policyFromInheriting |= policy;
        }
      }
      else if (getOriginalCell(c) == cellIndex && getOriginalSpan(c) == 1 && !(child instanceof Spacer)) {
        nonInheritingComponentsInCell++;
      }
    }
    if (nonInheritingComponentsInCell > 0) {
      return -1;
    }
    return policyFromInheriting;
  }

  public static Container findAlignedChild(final Component child, final GridConstraints c) {
    if (c.isUseParentLayout() && child instanceof Container) {
      Container container = (Container) child;
      if (container.getLayout() instanceof GridLayoutManager) {
        return container;
      }
      else if (container.getComponentCount() == 1 && container.getComponent(0) instanceof Container) {
        // "use parent layout" also needs to work in cases where a grid is the only control in a non-grid panel
        // which is contained in a grid
        Container childContainer = (Container) container.getComponent(0);
        if (childContainer.getLayout() instanceof GridLayoutManager) {
          return childContainer;
        }
      }
    }
    return null;
  }

  protected final Dimension getPreferredSize(final int componentIndex){
    Dimension size = myLayoutState.myPreferredSizes[componentIndex];
    if (size == null) {
      size = Util.getPreferredSize(myLayoutState.getComponent(componentIndex), myLayoutState.getConstraints(componentIndex), true);
      myLayoutState.myPreferredSizes[componentIndex] = size;
    }
    return size;
  }

  protected final Dimension getMinimumSize(final int componentIndex){
    Dimension size = myLayoutState.myMinimumSizes[componentIndex];
    if (size == null) {
      size = Util.getMinimumSize(myLayoutState.getComponent(componentIndex), myLayoutState.getConstraints(componentIndex), true);
      myLayoutState.myMinimumSizes[componentIndex] = size;
    }
    return size;
  }
}

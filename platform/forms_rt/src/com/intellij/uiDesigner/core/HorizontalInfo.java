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

  int getChildLayoutCellCount(final GridLayoutManager childLayout) {
    return childLayout.getColumnCount();
  }

  public int getMinimumWidth(final int componentIndex){
    return getMinimumSize(componentIndex).width;
  }

  public DimensionInfo getDimensionInfo(GridLayoutManager grid) {
    return grid.myHorizontalInfo;
  }

  public int getCellCount(){
    return myLayoutState.getColumnCount();
  }

  public int getPreferredWidth(final int componentIndex){
    return getPreferredSize(componentIndex).width;
  }
}

/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class GridConstraints implements Cloneable {
  public static final int FILL_NONE = 0;
  public static final int FILL_HORIZONTAL = 1;
  public static final int FILL_VERTICAL = 2;
  public static final int FILL_BOTH = FILL_HORIZONTAL | FILL_VERTICAL;

  /**
   * Put the component in the center of its display area.
   */
  public static final int ANCHOR_CENTER = 0;

  /**
   * Put the component at the top of its display area,
   * centered horizontally.
   */
  public static final int ANCHOR_NORTH = 1;

  /**
   * Put the component at the bottom of its display area, centered
   * horizontally.
   */
  public static final int ANCHOR_SOUTH = 2;

  /**
   * Put the component on the right side of its display area,
   * centered vertically.
   */
  public static final int ANCHOR_EAST = 4;

  /**
   * Put the component on the left side of its display area,
   * centered vertically.
   */
  public static final int ANCHOR_WEST = 8;

  /**
   * Put the component at the top-right corner of its display area.
   */
  public static final int ANCHOR_NORTHEAST = ANCHOR_NORTH | ANCHOR_EAST;

  /**
   * Put the component at the bottom-right corner of its display area.
   */
  public static final int ANCHOR_SOUTHEAST = ANCHOR_SOUTH | ANCHOR_EAST;

  /**
   * Put the component at the bottom-left corner of its display area.
   */
  public static final int ANCHOR_SOUTHWEST = ANCHOR_SOUTH | ANCHOR_WEST;


  /**
   * Put the component at the top-left corner of its display area.
   */
  public static final int ANCHOR_NORTHWEST = ANCHOR_NORTH | ANCHOR_WEST;

  /**
   * TODO[anton,vova] write javadoc
   */
  public static final int SIZEPOLICY_FIXED = 0;
  public static final int SIZEPOLICY_CAN_SHRINK = 1;
  public static final int SIZEPOLICY_CAN_GROW = 2;
  public static final int SIZEPOLICY_WANT_GROW = 4;

  public static final int ALIGN_LEFT   = 0;
  public static final int ALIGN_CENTER = 1;
  public static final int ALIGN_RIGHT  = 2;
  public static final int ALIGN_FILL   = 3;

  private int myRow;
  private int myColumn;
  private int myRowSpan;
  private int myColSpan;
  private int myVSizePolicy;
  private int myHSizePolicy;
  private int myFill;
  private int myAnchor;

  /**
   * @see #myPreferredSize
   */
  public final Dimension myMinimumSize;

  /**
   * overridden preferred size. Never <code>null</code>. Term "overridden" means that GridLayoutManager gets
   * preferred size from the constrains first. Moreover if one of the returned dimensions (x or y)
   * is negative then the corresponding dimension is also calculated by the component.
   * It means that it's possible to override any particular dimension without subclassing
   * of the component. Stantard Swing doesn't have such capability.
   */
  public final Dimension myPreferredSize;

  /**
   * @see #myPreferredSize
   */
  public final Dimension myMaximumSize;

  /**
   * Additonal offset of the component from the left part of its cell boundary, expressed in multiples
   * of the HIG-specified indent step.
   */
  private int myIndent;

  /**
   * If true, a component with grid layout embedded in a parent with grid layout uses the parent's
   * column sizes.
   */
  private boolean myUseParentLayout;

  public GridConstraints(){
    myRowSpan = 1;
    myColSpan = 1;
    myVSizePolicy = SIZEPOLICY_CAN_GROW | SIZEPOLICY_CAN_SHRINK;
    myHSizePolicy = SIZEPOLICY_CAN_GROW | SIZEPOLICY_CAN_SHRINK;
    myFill = FILL_NONE;
    myAnchor = ANCHOR_CENTER;
    myMinimumSize = new Dimension(-1, -1);
    myPreferredSize = new Dimension(-1, -1);
    myMaximumSize = new Dimension(-1, -1);
    myIndent = 0;
  }

  public GridConstraints(
    final int row,
    final int column,
    final int rowSpan,
    final int colSpan,
    final int anchor,
    final int fill,
    final int HSizePolicy,
    final int VSizePolicy,
    final Dimension minimumSize,
    final Dimension preferredSize,
    final Dimension maximumSize) {
    myRow = row;
    myColumn = column;
    myRowSpan = rowSpan;
    myColSpan = colSpan;
    myAnchor = anchor;
    myFill = fill;
    myHSizePolicy = HSizePolicy;
    myVSizePolicy = VSizePolicy;
    myMinimumSize = minimumSize != null ? new Dimension(minimumSize) : new Dimension(-1,-1);
    myPreferredSize = preferredSize != null ? new Dimension(preferredSize) : new Dimension(-1,-1);
    myMaximumSize = maximumSize != null ? new Dimension(maximumSize) : new Dimension(-1,-1);
    myIndent = 0;
  }

  public GridConstraints(
    final int row,
    final int column,
    final int rowSpan,
    final int colSpan,
    final int anchor,
    final int fill,
    final int HSizePolicy,
    final int VSizePolicy,
    final Dimension minimumSize,
    final Dimension preferredSize,
    final Dimension maximumSize,
    final int indent){
    this(row, column, rowSpan, colSpan, anchor, fill, HSizePolicy, VSizePolicy, minimumSize, preferredSize, maximumSize);
    myIndent = indent;
  }

  public GridConstraints(
    final int row,
    final int column,
    final int rowSpan,
    final int colSpan,
    final int anchor,
    final int fill,
    final int HSizePolicy,
    final int VSizePolicy,
    final Dimension minimumSize,
    final Dimension preferredSize,
    final Dimension maximumSize,
    final int indent,
    final boolean useParentLayout){
    this(row, column, rowSpan, colSpan, anchor, fill, HSizePolicy, VSizePolicy, minimumSize, preferredSize, maximumSize, indent);
    myUseParentLayout = useParentLayout;
  }

  /**
   * @return deep copy of the {@link GridConstraints}
   */
  public Object clone() {
    return new GridConstraints(
      getRow(), getColumn(), getRowSpan(), getColSpan(), getAnchor(), getFill(), getHSizePolicy(), getVSizePolicy(),
      new Dimension(myMinimumSize), new Dimension(myPreferredSize), new Dimension(myMinimumSize), getIndent(), isUseParentLayout()
    );
  }

  public int getColumn(){
    return myColumn;
  }

  public void setColumn(final int column){
    if (column < 0) {
      throw new IllegalArgumentException("wrong column: " + column);
    }
    myColumn = column;
  }

  public int getRow(){
    return myRow;
  }

  public void setRow(final int row){
    if(row<0){
      throw new IllegalArgumentException("wrong row: "+row);
    }
    myRow = row;
  }

  public int getRowSpan(){
    return myRowSpan;
  }

  public void setRowSpan(final int rowSpan){
    if (rowSpan <= 0) {
      throw new IllegalArgumentException("wrong rowSpan: " + rowSpan);
    }
    myRowSpan = rowSpan;
  }

  public int getColSpan(){
    return myColSpan;
  }

  public void setColSpan(final int colSpan){
    if (colSpan <= 0) {
      throw new IllegalArgumentException("wrong colSpan: " + colSpan);
    }
    myColSpan = colSpan;
  }

  public int getHSizePolicy(){
    return myHSizePolicy;
  }

  public void setHSizePolicy(final int sizePolicy){
    if (sizePolicy < 0 || sizePolicy > 7) {
      throw new IllegalArgumentException("invalid sizePolicy: " + sizePolicy);
    }
    myHSizePolicy = sizePolicy;
  }

  public int getVSizePolicy(){
    return myVSizePolicy;
  }

  public void setVSizePolicy(final int sizePolicy){
    if (sizePolicy < 0 || sizePolicy > 7) {
      throw new IllegalArgumentException("invalid sizePolicy: " + sizePolicy);
    }
    myVSizePolicy = sizePolicy;
  }

  public int getAnchor(){
    return myAnchor;
  }

  public void setAnchor(final int anchor){
    if (anchor < 0 || anchor > 15){
      throw new IllegalArgumentException("invalid anchor: " + anchor);
    }
    myAnchor = anchor;
  }

  public int getFill(){
    return myFill;
  }

  public void setFill(final int fill){
    if (
      fill != FILL_NONE &&
      fill != FILL_HORIZONTAL &&
      fill != FILL_VERTICAL &&
      fill != FILL_BOTH
    ){
      throw new IllegalArgumentException("invalid fill: " + fill);
    }
    myFill = fill;
  }

  public int getIndent() {
    return myIndent;
  }

  public void setIndent(final int indent) {
    myIndent = indent;
  }

  public boolean isUseParentLayout() {
    return myUseParentLayout;
  }

  public void setUseParentLayout(final boolean useParentLayout) {
    myUseParentLayout = useParentLayout;
  }

  public GridConstraints store() {
    final GridConstraints copy = new GridConstraints();

    copy.setRow(myRow);
    copy.setColumn(myColumn);
    copy.setColSpan(myColSpan);
    copy.setRowSpan(myRowSpan);
    copy.setVSizePolicy(myVSizePolicy);
    copy.setHSizePolicy(myHSizePolicy);
    copy.setFill(myFill);
    copy.setAnchor(myAnchor);
    copy.setIndent(myIndent);
    copy.setUseParentLayout(myUseParentLayout);

    copy.myMinimumSize.setSize(myMinimumSize);
    copy.myPreferredSize.setSize(myPreferredSize);
    copy.myMaximumSize.setSize(myMaximumSize);

    return copy;
  }

  public void restore(final GridConstraints constraints) {
    myRow = constraints.myRow;
    myColumn = constraints.myColumn;
    myRowSpan = constraints.myRowSpan;
    myColSpan = constraints.myColSpan;
    myHSizePolicy = constraints.myHSizePolicy;
    myVSizePolicy = constraints.myVSizePolicy;
    myFill = constraints.myFill;
    myAnchor = constraints.myAnchor;
    myIndent = constraints.myIndent;
    myUseParentLayout = constraints.myUseParentLayout;

    // Restore sizes
    myMinimumSize.setSize(constraints.myMinimumSize);
    myPreferredSize.setSize(constraints.myPreferredSize);
    myMaximumSize.setSize(constraints.myMaximumSize);
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof GridConstraints)) return false;

    final GridConstraints gridConstraints = (GridConstraints)o;

    if (myAnchor != gridConstraints.myAnchor) return false;
    if (myColSpan != gridConstraints.myColSpan) return false;
    if (myColumn != gridConstraints.myColumn) return false;
    if (myFill != gridConstraints.myFill) return false;
    if (myHSizePolicy != gridConstraints.myHSizePolicy) return false;
    if (myRow != gridConstraints.myRow) return false;
    if (myRowSpan != gridConstraints.myRowSpan) return false;
    if (myVSizePolicy != gridConstraints.myVSizePolicy) return false;
    if (myMaximumSize != null ? !myMaximumSize.equals(gridConstraints.myMaximumSize) : gridConstraints.myMaximumSize != null) return false;
    if (myMinimumSize != null ? !myMinimumSize.equals(gridConstraints.myMinimumSize) : gridConstraints.myMinimumSize != null) return false;
    if (myPreferredSize != null ? !myPreferredSize.equals(gridConstraints.myPreferredSize) : gridConstraints.myPreferredSize != null) return false;
    if (myIndent != gridConstraints.myIndent) return false;
    if (myUseParentLayout != gridConstraints.myUseParentLayout) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = myRow;
    result = 29 * result + myColumn;
    result = 29 * result + myRowSpan;
    result = 29 * result + myColSpan;
    result = 29 * result + myVSizePolicy;
    result = 29 * result + myHSizePolicy;
    result = 29 * result + myFill;
    result = 29 * result + myAnchor;
    result = 29 * result + (myMinimumSize != null ? myMinimumSize.hashCode() : 0);
    result = 29 * result + (myPreferredSize != null ? myPreferredSize.hashCode() : 0);
    result = 29 * result + (myMaximumSize != null ? myMaximumSize.hashCode() : 0);
    result = 29 * result + myIndent;
    result = 29 * result + (myUseParentLayout ? 1 : 0);
    return result;
  }

  public int getCell(final boolean isRow){
    return isRow ? getRow() : getColumn();
  }

  public void setCell(final boolean isRow, final int value) {
    if (isRow) {
      setRow(value);
    }
    else {
      setColumn(value);
    }
  }

  public int getSpan(final boolean isRow){
    return isRow ? getRowSpan() : getColSpan();
  }

  public void setSpan(final boolean isRow, final int value) {
    if (isRow) {
      setRowSpan(value);
    }
    else {
      setColSpan(value);
    }
  }

  public boolean contains(final boolean isRow, final int cell) {
    if (isRow) {
      return cell >= myRow && cell < myRow + myRowSpan;
    }
    return cell >= myColumn && cell < myColumn + myColSpan;
  }

  public String toString() {
    //noinspection HardCodedStringLiteral
    return "GridConstraints (row=" + myRow + ", col=" + myColumn + ", rowspan=" + myRowSpan + ", colspan=" + myColSpan + ")";
  }
}

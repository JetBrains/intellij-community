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

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

public final class GridLayoutManager extends AbstractLayout {
  /**
   * Minimum size of the cell (row/column). In design mode this constant should be greater than zero
   */
  private int myMinCellSize = 20;
  /**
   * Rows' stretches. These are positive integer values. The default value for
   * any row is <code>1</code>.
   */
  private final int[] myRowStretches;
  /**
   * Columns' stretches. These are positive integer values. The default value for
   * any column is <code>1</code>.
   */
  private final int[] myColumnStretches;
  /**
   * Arrays of rows' heights. Method layoutContainer sets this member each time
   * it's invoked.
   * This is <code>getRowCount()x2</code> two dimensional array. <code>[i][0]</code>
   * is top <code>y</code> coordinate of row with index <code>i</code>. This <code>y</code>
   * coordinate is in the container coordinate system.
   * <code>[i][1]</code> is width of the row with index <code>i</code>.
   */
  private final int[] myYs;
  private final int[] myHeights;
  /**
   * Arrays of columns' widths. Method layoutContainer sets this member each time
   * it's invoked.
   * This is <code>getColumnCount()x2</code> two dimensional array. <code>[i][0]</code>
   * is left <code>x</code> coordinate of row with index <code>i</code>. This <code>x</code>
   * coordinate is in the container coordinate system.
   * <code>[i][1]</code> is width of the column with index <code>i</code>.
   */
  private final int[] myXs;
  private final int[] myWidths;

  private LayoutState myLayoutState;
  /**
   * package local because is used in tests
   */
  DimensionInfo myHorizontalInfo;
  /**
   * package local because is used in tests
   */
  DimensionInfo myVerticalInfo;

  private boolean mySameSizeHorizontally;
  private boolean mySameSizeVertically;

  /**
   * Key for accessing client property which is set on the root Swing component of the design-time component
   * hierarchy and specifies the value of extra insets added to all components.
   */
  public static Object DESIGN_TIME_INSETS = new Object();

  private static final int SKIP_ROW = 1;
  private static final int SKIP_COL = 2;

  public GridLayoutManager(final int rowCount, final int columnCount) {
    if (columnCount < 1) {
      throw new IllegalArgumentException("wrong columnCount: " + columnCount);
    }
    if (rowCount < 1) {
      throw new IllegalArgumentException("wrong rowCount: " + rowCount);
    }

    myRowStretches = new int[rowCount];
    for (int i = 0; i < rowCount; i++) {
      myRowStretches[i] = 1;
    }
    myColumnStretches = new int[columnCount];
    for (int i = 0; i < columnCount; i++) {
      myColumnStretches[i] = 1;
    }

    myXs = new int[columnCount];
    myWidths = new int[columnCount];

    myYs = new int[rowCount];
    myHeights = new int[rowCount];
  }

  /**
   * don't delete this constructor! don't use this constructor!!! should be used ONLY in generated code or in tests
   */
  public GridLayoutManager(final int rowCount, final int columnCount, final Insets margin, final int hGap, final int vGap) {
    this(rowCount, columnCount);
    setMargin(margin);
    setHGap(hGap);
    setVGap(vGap);
    myMinCellSize = 0;
  }

  /**
   * don't delete this constructor! don't use this constructor!!! should be used ONLY in generated code or in tests
   */
  public GridLayoutManager(
    final int rowCount,
    final int columnCount,
    final Insets margin,
    final int hGap,
    final int vGap,
    final boolean sameSizeHorizontally,
    final boolean sameSizeVertically
  ) {
    this(rowCount, columnCount, margin, hGap, vGap);
    mySameSizeHorizontally = sameSizeHorizontally;
    mySameSizeVertically = sameSizeVertically;
  }

  public void addLayoutComponent(final Component comp, final Object constraints) {
    final GridConstraints c = (GridConstraints)constraints;
    final int row = c.getRow();
    final int rowSpan = c.getRowSpan();
    final int rowCount = getRowCount();
    if (row < 0 || row >= rowCount) {
      throw new IllegalArgumentException("wrong row: " + row);
    }
    if (row + rowSpan - 1 >= rowCount) {
      throw new IllegalArgumentException("wrong row span: " + rowSpan + "; row=" + row + " rowCount=" + rowCount);
    }
    final int column = c.getColumn();
    final int colSpan = c.getColSpan();
    final int columnCount = getColumnCount();
    if (column < 0 || column >= columnCount) {
      throw new IllegalArgumentException("wrong column: " + column);
    }
    if (column + colSpan - 1 >= columnCount) {
      throw new IllegalArgumentException(
        "wrong col span: " + colSpan + "; column=" + column + " columnCount=" + columnCount);
    }
    super.addLayoutComponent(comp, constraints);
  }

  /**
   * @return number of rows in the grid.
   */
  public int getRowCount() {
    return myRowStretches.length;
  }

  /**
   * @return number of columns in the grid.
   */
  public int getColumnCount() {
    return myColumnStretches.length;
  }

  /**
   * @return vertical stretch for the row with specified index. The returned
   *         values is in range <code>[1..Integer.MAX_VALUE]</code>.
   */
  public int getRowStretch(final int rowIndex) {
    return myRowStretches[rowIndex];
  }

  /**
   * @throws IllegalArgumentException if <code>stretch</code> is less
   *                                  then <code>1</code>.
   */
  public void setRowStretch(final int rowIndex, final int stretch) {
    if (stretch < 1) {
      throw new IllegalArgumentException("wrong stretch: " + stretch);
    }
    myRowStretches[rowIndex] = stretch;
  }

  /**
   * @return maximum horizontal stretch for the component which are located
   *         at the specified column.
   */
  public int getColumnStretch(final int columnIndex) {
    return myColumnStretches[columnIndex];
  }

  /**
   * @throws IllegalArgumentException if <code>stretch</code> is less
   *                                  then <code>1</code>.
   */
  public void setColumnStretch(final int columnIndex, final int stretch) {
    if (stretch < 1) {
      throw new IllegalArgumentException("wrong stretch: " + stretch);
    }
    myColumnStretches[columnIndex] = stretch;
  }

  public Dimension maximumLayoutSize(final Container target) {
    return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
  }

  public Dimension minimumLayoutSize(final Container container) {
    validateInfos(container);
    
    // IMPORTANT!!! DO NOT INLINE!!! 
    final DimensionInfo horizontalInfo = myHorizontalInfo;
    final DimensionInfo verticalInfo = myVerticalInfo;

    final Dimension result = getTotalGap(container, horizontalInfo, verticalInfo);

    final int[] widths = getMinSizes(horizontalInfo);
    if (mySameSizeHorizontally) {
      makeSameSizes(widths);
    }
    result.width += sum(widths);

    final int[] heights = getMinSizes(verticalInfo);
    if (mySameSizeVertically) {
      makeSameSizes(heights);
    }
    result.height += sum(heights);

    return result;
  }

  private static void makeSameSizes(int[] widths) {
    int max = widths[0];
    for (int i = 0; i < widths.length; i++) {
      int width = widths[i];
      max = Math.max(width, max);
    }

    for (int i = 0; i < widths.length; i++) {
      widths[i] = max;
    }
  }

  private static int[] getSameSizes(DimensionInfo info, int totalWidth) {
    int[] widths = new int[info.getCellCount()];

    int average = totalWidth / widths.length;
    int rest = totalWidth % widths.length;

    for (int i = 0; i < widths.length; i++) {
      widths[i] = average;
      if (rest > 0) {
        widths[i]++;
        rest--;
      }
    }

    return widths;
  }

  public Dimension preferredLayoutSize(final Container container) {
    validateInfos(container);

    // IMPORTANT!!! DO NOT INLINE!!! 
    final DimensionInfo horizontalInfo = myHorizontalInfo;
    final DimensionInfo verticalInfo = myVerticalInfo;

    final Dimension result = getTotalGap(container, horizontalInfo, verticalInfo);

    final int[] widths = getPrefSizes(horizontalInfo);
    if (mySameSizeHorizontally) {
      makeSameSizes(widths);
    }
    result.width += sum(widths);

    final int[] heights = getPrefSizes(verticalInfo);
    if (mySameSizeVertically) {
      makeSameSizes(heights);
    }
    result.height += sum(heights);

    return result;
  }

  private static int sum(final int[] ints) {
    int result = 0;
    for (int i = ints.length - 1; i >= 0; i--) {
      result += ints[i];
    }
    return result;
  }

  private Dimension getTotalGap(final Container container, final DimensionInfo hInfo, final DimensionInfo vInfo) {
    final Insets insets = getInsets(container);
    return new Dimension(
      insets.left + insets.right + countGap(hInfo, 0, hInfo.getCellCount()) + myMargin.left + myMargin.right,
      insets.top + insets.bottom + countGap(vInfo, 0, vInfo.getCellCount()) + myMargin.top + myMargin.bottom);
  }

  private static int getDesignTimeInsets(Container container) {
    while(container != null) {
      if (container instanceof JComponent) {
        Integer designTimeInsets = (Integer)((JComponent) container).getClientProperty(DESIGN_TIME_INSETS);
        if (designTimeInsets != null) {
          return designTimeInsets.intValue();
        }
      }
      container = container.getParent();
    }
    return 0;
  }

  private static Insets getInsets(Container container) {
    final Insets insets = container.getInsets();
    int insetsValue = getDesignTimeInsets(container);
    if (insetsValue != 0) {
      return new Insets(insets.top+insetsValue, insets.left+insetsValue,
                        insets.bottom+insetsValue, insets.right+insetsValue);
    }
    return insets;
  }

  private static int countGap(final DimensionInfo info, final int startCell, final int cellCount) {
    int counter = 0;
    for (int cellIndex = startCell + cellCount - 2 /*gap after last cell should not be counted*/;
         cellIndex >= startCell;
         cellIndex--) {
      if (shouldAddGapAfterCell(info, cellIndex)) {
        counter++;
      }
    }
    return counter * info.getGap();
  }

  private static boolean shouldAddGapAfterCell(final DimensionInfo info, final int cellIndex) {
    if (cellIndex < 0 || cellIndex >= info.getCellCount()) {
      throw new IllegalArgumentException("wrong cellIndex: " + cellIndex + "; cellCount=" + info.getCellCount());
    }

    boolean endsInThis = false;
    boolean startsInNext = false;

    int indexOfNextNotEmpty = -1;
    for (int i = cellIndex + 1; i < info.getCellCount(); i++) {
      if (!isCellEmpty(info, i)) {
        indexOfNextNotEmpty = i;
        break;
      }
    }

    for (int i = 0; i < info.getComponentCount(); i++) {
      final Component component = info.getComponent(i);
      if (component instanceof Spacer) {
        continue;
      }

      if (info.componentBelongsCell(i, cellIndex) &&
          DimensionInfo.findAlignedChild(component, info.getConstraints(i)) != null) {
        return true;
      }

      if (info.getCell(i) == indexOfNextNotEmpty) {
        startsInNext = true;
      }

      if (info.getCell(i) + info.getSpan(i) - 1 == cellIndex) {
        endsInThis = true;
      }
    }

    return startsInNext && endsInThis;
  }

  private static boolean isCellEmpty(final DimensionInfo info, final int cellIndex) {
    if (cellIndex < 0 || cellIndex >= info.getCellCount()) {
      throw new IllegalArgumentException("wrong cellIndex: " + cellIndex + "; cellCount=" + info.getCellCount());
    }
    for (int i = 0; i < info.getComponentCount(); i++) {
      final Component component = info.getComponent(i);
      if (info.getCell(i) == cellIndex && !(component instanceof Spacer)) {
        return false;
      }
    }
    return true;
  }

  public void layoutContainer(final Container container) {
    validateInfos(container);

    // IMPORTANT!!! DO NOT INLINE!!! 
    final LayoutState layoutState = myLayoutState;
    final DimensionInfo horizontalInfo = myHorizontalInfo;
    final DimensionInfo verticalInfo = myVerticalInfo;

    Insets insets = getInsets(container);

    int skipLayout = checkSetSizesFromParent(container, insets);

    final Dimension gap = getTotalGap(container, horizontalInfo, verticalInfo);

    final Dimension size = container.getSize();
    size.width -= gap.width;
    size.height -= gap.height;

    final Dimension prefSize = preferredLayoutSize(container);
    prefSize.width -= gap.width;
    prefSize.height -= gap.height;

    final Dimension minSize = minimumLayoutSize(container);
    minSize.width -= gap.width;
    minSize.height -= gap.height;

    // Calculate rows' heights
    if ((skipLayout & SKIP_ROW) == 0) {
      final int[] heights;
      if (mySameSizeVertically) {
        heights = getSameSizes(verticalInfo, Math.max(size.height, minSize.height));
      }
      else {
        if (size.height < prefSize.height) {
          heights = getMinSizes(verticalInfo);
          new_doIt(heights, 0, verticalInfo.getCellCount(), size.height, verticalInfo, true);
        }
        else {
          heights = getPrefSizes(verticalInfo);
          new_doIt(heights, 0, verticalInfo.getCellCount(), size.height, verticalInfo, false);
        }
      }

      // Calculate rows' bounds
      int y = insets.top + myMargin.top;
      for (int i = 0; i < heights.length; i++) {
        myYs[i] = y;
        myHeights[i] = heights[i];
        y += heights[i];
        if (shouldAddGapAfterCell(verticalInfo, i)) {
          y += verticalInfo.getGap();
        }
      }
    }

    if ((skipLayout & SKIP_COL) == 0) {
      // Calculate columns' widths
      final int[] widths;
      if (mySameSizeHorizontally) {
        widths = getSameSizes(horizontalInfo, Math.max(size.width, minSize.width));
      }
      else {
        if (size.width < prefSize.width) {
          widths = getMinSizes(horizontalInfo);
          new_doIt(widths, 0, horizontalInfo.getCellCount(), size.width, horizontalInfo, true);
        }
        else {
          widths = getPrefSizes(horizontalInfo);
          new_doIt(widths, 0, horizontalInfo.getCellCount(), size.width, horizontalInfo, false);
        }
      }

      // Calculate columns' bounds
      int x = insets.left + myMargin.left;
      for (int i = 0; i < widths.length; i++) {
        myXs[i] = x;
        myWidths[i] = widths[i];
        x += widths[i];
        if (shouldAddGapAfterCell(horizontalInfo, i)) {
          x += horizontalInfo.getGap();
        }
      }
    }

    // Set bounds of components
    for (int i = 0; i < layoutState.getComponentCount(); i++) {
      final GridConstraints c = layoutState.getConstraints(i);
      final Component component = layoutState.getComponent(i);

      final int column = horizontalInfo.getCell(i);
      final int colSpan = horizontalInfo.getSpan(i);
      final int row = verticalInfo.getCell(i);
      final int rowSpan = verticalInfo.getSpan(i);

      final int cellWidth = myXs[column + colSpan - 1] + myWidths[column + colSpan - 1] - myXs[column];
      final int cellHeight = myYs[row + rowSpan - 1] + myHeights[row + rowSpan - 1] - myYs[row];

      final Dimension componentSize = new Dimension(cellWidth, cellHeight);

      if ((c.getFill() & GridConstraints.FILL_HORIZONTAL) == 0) {
        componentSize.width = Math.min(componentSize.width, horizontalInfo.getPreferredWidth(i));
      }

      if ((c.getFill() & GridConstraints.FILL_VERTICAL) == 0) {
        componentSize.height = Math.min(componentSize.height, verticalInfo.getPreferredWidth(i));
      }

      Util.adjustSize(component, c, componentSize);

      int dx = 0;
      int dy = 0;

      if ((c.getAnchor() & GridConstraints.ANCHOR_EAST) != 0) {
        dx = cellWidth - componentSize.width;
      }
      else if ((c.getAnchor() & GridConstraints.ANCHOR_WEST) == 0) {
        dx = (cellWidth - componentSize.width) / 2;
      }

      if ((c.getAnchor() & GridConstraints.ANCHOR_SOUTH) != 0) {
        dy = cellHeight - componentSize.height;
      }
      else if ((c.getAnchor() & GridConstraints.ANCHOR_NORTH) == 0) {
        dy = (cellHeight - componentSize.height) / 2;
      }

      int indent = Util.DEFAULT_INDENT * c.getIndent();
      componentSize.width -= indent;
      dx += indent;

      component.setBounds(myXs[column] + dx, myYs[row] + dy, componentSize.width, componentSize.height);
    }
  }

  private int checkSetSizesFromParent(final Container container, final Insets insets) {
    int skipLayout = 0;

    GridLayoutManager parentGridLayout = null;
    GridConstraints parentGridConstraints = null;
    // "use parent layout" also needs to work in cases where a grid is the only control in a non-grid panel
    // which is contained in a grid
    Container parent = container.getParent();
    if (parent != null) {
      if (parent.getLayout() instanceof GridLayoutManager) {
        parentGridLayout = (GridLayoutManager) parent.getLayout();
        parentGridConstraints = parentGridLayout.getConstraintsForComponent(container);
      }
      else {
        Container parent2 = parent.getParent();
        if (parent2 != null && parent2.getLayout() instanceof GridLayoutManager) {
          parentGridLayout = (GridLayoutManager) parent2.getLayout();
          parentGridConstraints = parentGridLayout.getConstraintsForComponent(parent);
        }
      }
    }

    if (parentGridLayout != null && parentGridConstraints.isUseParentLayout()) {
      if (myRowStretches.length == parentGridConstraints.getRowSpan()) {
        int row = parentGridConstraints.getRow();
        myYs[0] = insets.top + myMargin.top;
        myHeights[0] = parentGridLayout.myHeights[row] - myYs[0];
        for (int i = 1; i < myRowStretches.length; i++) {
          myYs[i] = parentGridLayout.myYs[i + row] - parentGridLayout.myYs[row];
          myHeights[i] = parentGridLayout.myHeights[i + row];
        }
        myHeights[myRowStretches.length - 1] -= insets.bottom + myMargin.bottom;
        skipLayout |= SKIP_ROW;
      }
      if (myColumnStretches.length == parentGridConstraints.getColSpan()) {
        int col = parentGridConstraints.getColumn();
        myXs[0] = insets.left + myMargin.left;
        myWidths[0] = parentGridLayout.myWidths[col] - myXs[0];
        for (int i = 1; i < myColumnStretches.length; i++) {
          myXs[i] = parentGridLayout.myXs[i + col] - parentGridLayout.myXs[col];
          myWidths[i] = parentGridLayout.myWidths[i + col];
        }
        myWidths[myColumnStretches.length - 1] -= insets.right + myMargin.right;
        skipLayout |= SKIP_COL;
      }
    }
    return skipLayout;
  }

  public void invalidateLayout(final Container container) {
    myLayoutState = null;
    myHorizontalInfo = null;
    myVerticalInfo = null;
  }

  void validateInfos(final Container container) {
    if (myLayoutState == null) {
      // TODO[yole]: Implement cleaner way of determining whether invisible components should be ignored
      myLayoutState = new LayoutState(this, getDesignTimeInsets(container) == 0);
      myHorizontalInfo = new HorizontalInfo(myLayoutState, getHGapImpl(container));
      myVerticalInfo = new VerticalInfo(myLayoutState, getVGapImpl(container));
    }
  }

  /**
   * for design time only
   */
  public int[] getXs() {
    return myXs;
  }

  /**
   * for design time only
   */
  public int[] getWidths() {
    return myWidths;
  }

  /**
   * for design time only
   */
  public int[] getYs() {
    return myYs;
  }

  /**
   * for design time only
   */
  public int[] getHeights() {
    return myHeights;
  }

  public int[] getCoords(boolean isRow) {
    return isRow ? myYs : myXs;
  }

  public int[] getSizes(boolean isRow) {
    return isRow ? myHeights : myWidths;
  }

  private int[] getMinSizes(final DimensionInfo info) {
    return getMinOrPrefSizes(info, true);
  }

  private int[] getPrefSizes(final DimensionInfo info) {
    return getMinOrPrefSizes(info, false);
  }

  private int[] getMinOrPrefSizes(final DimensionInfo info, final boolean min) {
    final int[] widths = new int[info.getCellCount()];
    for (int i = 0; i < widths.length; i++) {
      widths[i] = myMinCellSize;
    }

    // single spaned components
    for (int i = info.getComponentCount() - 1; i >= 0; i--) {
      if (info.getSpan(i) != 1) {
        continue;
      }

      int size = min ? getMin2(info, i) : Math.max(info.getMinimumWidth(i), info.getPreferredWidth(i));
      final int gap = countGap(info, info.getCell(i), info.getSpan(i));
      size = Math.max(size - gap, 0);

      widths[info.getCell(i)] = Math.max(widths[info.getCell(i)], size);
    }

    // components inheriting layout from us
    updateSizesFromChildren(info, min, widths);

    // multispanned components

    final boolean[] toProcess = new boolean[info.getCellCount()];

    for (int i = info.getComponentCount() - 1; i >= 0; i--) {
      int size = min ? getMin2(info, i) : Math.max(info.getMinimumWidth(i), info.getPreferredWidth(i));

      final int span = info.getSpan(i);
      final int cell = info.getCell(i);

      final int gap = countGap(info, cell, span);
      size = Math.max(size - gap, 0);

      Arrays.fill(toProcess, false);

      int curSize = 0;
      for (int j=0; j < span; j++){
        curSize += widths[j + cell];
        toProcess[j + cell] = true;
      }

      if (curSize >= size) {
        continue;
      }

      final boolean[] higherPriorityCells = new boolean[toProcess.length];
      getCellsWithHigherPriorities(info, toProcess, higherPriorityCells, false, widths);

      distribute(higherPriorityCells, info, size - curSize, widths);
    }

    return widths;
  }

  private static void updateSizesFromChildren(final DimensionInfo info, final boolean min, final int[] widths) {
    for(int i=info.getComponentCount() - 1; i >= 0; i--) {
      Component child = info.getComponent(i);
      GridConstraints c = info.getConstraints(i);
      if (c.isUseParentLayout() && child instanceof Container) {
        Container container = (Container) child;
        if (container.getLayout() instanceof GridLayoutManager) {
          updateSizesFromChild(info, min, widths, container, i);
        }
        else if (container.getComponentCount() == 1 && container.getComponent(0) instanceof Container) {
          // "use parent layout" also needs to work in cases where a grid is the only control in a non-grid panel
          // which is contained in a grid
          Container childContainer = (Container) container.getComponent(0);
          if (childContainer.getLayout() instanceof GridLayoutManager) {
            updateSizesFromChild(info, min, widths, childContainer, i);
          }
        }
      }
    }
  }

  private static void updateSizesFromChild(final DimensionInfo info,
                                           final boolean min,
                                           final int[] widths,
                                           final Container container,
                                           final int childIndex) {
    GridLayoutManager childLayout = (GridLayoutManager) container.getLayout();
    if (info.getSpan(childIndex) == info.getChildLayoutCellCount(childLayout)) {
      childLayout.validateInfos(container);
      DimensionInfo childInfo = (info instanceof HorizontalInfo)
                                ? childLayout.myHorizontalInfo
                                : childLayout.myVerticalInfo;
      int[] sizes = childLayout.getMinOrPrefSizes(childInfo, min);
      int cell = info.getCell(childIndex);
      for(int j=0; j<sizes.length; j++) {
        widths [cell+j] = Math.max(widths [cell+j], sizes [j]);
      }
    }
  }


  private static int getMin2(final DimensionInfo info, final int componentIndex) {
    final int s;
    if ((info.getSizePolicy(componentIndex) & GridConstraints.SIZEPOLICY_CAN_SHRINK) != 0) {
      s = info.getMinimumWidth(componentIndex);
    }
    else {
      // it might be possible that minSize > prefSize (for example, only min is set in constraints to 100 and
      // JComponent's preferred size returned is 20)
      s = Math.max(info.getMinimumWidth(componentIndex), info.getPreferredWidth(componentIndex));
    }
    return s;
  }

  /**
   * @param widths in/out parameter
   */
  private void new_doIt(final int[] widths, final int cell, final int span, final int minWidth, final DimensionInfo info, final boolean checkPrefs) {
    int toDistribute = minWidth;

    for (int i = cell; i < cell + span; i++) {
      toDistribute -= widths[i];
    }
    if (toDistribute <= 0) {
      return;
    }

    final boolean[] allowedCells = new boolean[info.getCellCount()];
    for (int i = cell; i < cell + span; i++) {
      allowedCells[i] = true;
    }

    final boolean[] higherPriorityCells = new boolean[info.getCellCount()];
    getCellsWithHigherPriorities(info, allowedCells, higherPriorityCells, checkPrefs, widths);

    distribute(higherPriorityCells, info, toDistribute, widths);
  }

  private static void distribute(final boolean[] higherPriorityCells, final DimensionInfo info, int toDistribute, final int[] widths) {
    int stretches = 0;
    for (int i = 0; i < info.getCellCount(); i++) {
      if (higherPriorityCells[i]) {
        stretches += info.getStretch(i);
      }
    }

    {
      final int toDistributeFrozen = toDistribute;
      for (int i = 0; i < info.getCellCount(); i++) {
        if (!higherPriorityCells[i]) {
          continue;
        }

        final int addon = toDistributeFrozen * info.getStretch(i) / stretches;
        widths[i] += addon;

        toDistribute -= addon;
      }
    }

    if (toDistribute != 0) {
      for (int i = 0; i < info.getCellCount(); i++) {
        if (!higherPriorityCells[i]) {
          continue;
        }

        widths[i]++;
        toDistribute--;

        if (toDistribute == 0) {
          break;
        }
      }
    }

    if (toDistribute != 0) {
      throw new IllegalStateException("toDistribute = " + toDistribute);
    }
  }

  private void getCellsWithHigherPriorities(
    final DimensionInfo info,
    final boolean[] allowedCells,
    final boolean[] higherPriorityCells,
    final boolean checkPrefs,
    final int[] widths
  ) {
    Arrays.fill(higherPriorityCells, false);

    int foundCells = 0;

    if (checkPrefs) {
      // less that preferred size
      final int[] prefs = getMinOrPrefSizes(info, false);
      for (int cell = 0; cell < allowedCells.length; cell++) {
        if (!allowedCells[cell]) {
          continue;
        }
        if (!isCellEmpty(info, cell) && prefs[cell] > widths[cell]) {
          higherPriorityCells[cell] = true;
          foundCells++;
        }
      }

      if (foundCells > 0) {
        return;
      }
    }

    // want grow
    for (int cell = 0; cell < allowedCells.length; cell++) {
      if (!allowedCells[cell]) {
        continue;
      }
      if ((info.getCellSizePolicy(cell) & GridConstraints.SIZEPOLICY_WANT_GROW) != 0) {
        higherPriorityCells[cell] = true;
        foundCells++;
      }
    }

    if (foundCells > 0) {
      return;
    }

    // can grow
    for (int cell = 0; cell < allowedCells.length; cell++) {
      if (!allowedCells[cell]) {
        continue;
      }
      if ((info.getCellSizePolicy(cell) & GridConstraints.SIZEPOLICY_CAN_GROW) != 0) {
        higherPriorityCells[cell] = true;
        foundCells++;
      }
    }

    if (foundCells > 0) {
      return;
    }

    // non empty
    for (int cell = 0; cell < allowedCells.length; cell++) {
      if (!allowedCells[cell]) {
        continue;
      }
      if (!isCellEmpty(info, cell)) {
        higherPriorityCells[cell] = true;
        foundCells++;
      }
    }

    if (foundCells > 0) {
      return;
    }

    // any
    for (int cell = 0; cell < allowedCells.length; cell++) {
      if (!allowedCells[cell]) {
        continue;
      }
      higherPriorityCells[cell] = true;
    }
  }

  public boolean isSameSizeHorizontally() {
    return mySameSizeHorizontally;
  }

  public boolean isSameSizeVertically() {
    return mySameSizeVertically;
  }

  public void setSameSizeHorizontally(boolean sameSizeHorizontally) {
    mySameSizeHorizontally = sameSizeHorizontally;
  }

  public void setSameSizeVertically(boolean sameSizeVertically) {
    mySameSizeVertically = sameSizeVertically;
  }

  public int[] getHorizontalGridLines() {
    int[] result = new int [myYs.length+1];
    result [0] = myYs [0];
    for(int i=0; i<myYs.length-1; i++) {
      result [i+1] = (myYs[i] + myHeights[i] + myYs[i + 1]) / 2;
    }
    result [myYs.length] = myYs [myYs.length-1] + myHeights [myYs.length-1];
    return result;
  }

  public int[] getVerticalGridLines() {
    int[] result = new int [myXs.length+1];
    result [0] = myXs [0];
    for(int i=0; i<myXs.length-1; i++) {
      result [i+1] = (myXs[i] + myWidths[i] + myXs[i + 1]) / 2;
    }
    result [myXs.length] = myXs [myXs.length-1] + myWidths [myXs.length-1];
    return result;
  }

  public int getCellCount(final boolean isRow) {
    return isRow ? getRowCount() : getColumnCount();
  }

  public int getCellSizePolicy(final boolean isRow, final int cellIndex) {
    DimensionInfo info = isRow ? myVerticalInfo : myHorizontalInfo;
    if (info == null) {
      // not laid out yet
      return 0;
    }
    return info.getCellSizePolicy(cellIndex);
  }
}

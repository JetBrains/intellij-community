/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.uiDesigner.core;

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

  private void makeSameSizes(int[] widths) {
    int max = widths[0];
    for (int i = 0; i < widths.length; i++) {
      int width = widths[i];
      max = Math.max(width, max);
    }

    for (int i = 0; i < widths.length; i++) {
      widths[i] = max;
    }
  }

  private int[] getSameSizes(DimensionInfo info, int totalWidth) {
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
    final Insets insets = container.getInsets();
    return new Dimension(
      insets.left + insets.right + countGap(hInfo, 0, hInfo.getCellCount()) + myMargin.left + myMargin.right,
      insets.top + insets.bottom + countGap(vInfo, 0, vInfo.getCellCount()) + myMargin.top + myMargin.bottom);
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

    final Insets insets = container.getInsets();

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

    // Calculate rows' widths
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

      component.setBounds(myXs[column] + dx, myYs[row] + dy, componentSize.width, componentSize.height);
    }
  }

  public void invalidateLayout(final Container container) {
    myLayoutState = null;
    myHorizontalInfo = null;
    myVerticalInfo = null;
  }

  private void validateInfos(final Container container) {
    if (myLayoutState == null) {
      myLayoutState = new LayoutState(this, true);
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

  /**
   * @return index of the row that contains point with <code>y</code> coordinate.
   *         If <code>y</code> doesn't belong to any row then the method returns <code>-1</code>.
   *         Note, that <code>y</code> is in <code>group</code> coordinate system.
   */
  public int getRowAt(final int y) {
    for (int i = 0; i < myYs.length; i++) {
      if (myYs[i] <= y && y <= myYs[i] + myHeights[i]) {
        return i;
      }
    }
    return -1;
  }

  /**
   * @return index of the column that contains point with <code>x</code> coordinate.
   *         Note, that <code>x</code> is in <code>group</code> coordinate system.
   */
  public int getColumnAt(final int x) {
    for (int i = 0; i < myXs.length; i++) {
      if (myXs[i] <= x && x <= myXs[i] + myWidths[i]) {
        return i;
      }
    }
    return -1;
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
}
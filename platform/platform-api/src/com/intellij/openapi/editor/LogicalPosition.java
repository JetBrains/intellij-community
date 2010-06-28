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
package com.intellij.openapi.editor;

import org.jetbrains.annotations.NonNls;

/**
 * Represents a logical position in the editor. Logical positions ignore folding -
 * for example, if the top 10 lines of the document are folded, the 10th line in the document
 * will have the line number 10 in its logical position.
 * <p/>
 * Logical position may store additional parameters that define its mapping to {@link VisualPosition}. Rationale is that
 * single logical <code>(line; column)</code> pair matches soft wrap-introduced virtual space, i.e. different visual positions
 * correspond to the same logical position. It's convenient to store exact visual location details within the logical
 * position in order to relief further {@code 'logical position' -> 'visual position'} mapping.
 * <p/>
 * <b>Note:</b> two objects of this class are considered equal if their logical line and column are equal. I.e. all logical positions
 * for soft wrap-introduced virtual space and the first document symbol after soft wrap are considered to be equal.
 *
 * @see Editor#offsetToLogicalPosition(int)
 * @see Editor#logicalPositionToOffset(LogicalPosition)
 *
 * @see VisualPosition
 * @see Editor#visualToLogicalPosition(VisualPosition)
 *
 * @see Editor#xyToLogicalPosition(java.awt.Point)
 */
public class LogicalPosition implements Comparable<LogicalPosition> {
  public final int line;
  public final int column;

  /**
   * Identifies if current logical position may be correctly mapped to visual position. E.g. we can define properties like
   * {@link #softWrapLines}, {@link #softWrapColumnDiff} etc during {@code 'visual position' -> 'logical position'} conversion
   * in order to be able to easy match it back to visual position.
   */
  public final boolean visualPositionAware;

  /**
   * Number of virtual soft wrap-introduced lines before the visual position that corresponds to the current logical position.
   *
   * @see #visualPositionAware
   */
  public final int softWrapLines;

  /**
   * Number of virtual soft lines introduced by the current soft wrap before the visual position that corresponds
   * to the current logical position.
   * <p/>
   * This value is assumed to be not greater than {@link #softWrapLines} all the time.
   *
   * @see #visualPositionAware
   */
  public final int linesFromActiveSoftWrap;

  /**
   * Number to add to the {@link #column logical column} in order to get soft wrap-introduced visual column offset.
   *
   * @see #visualPositionAware
   */
  public final int softWrapColumnDiff;

  /**
   * Number of folded line feeds before the current position.
   *
   * @see #visualPositionAware
   */
  public final int foldedLines;

  /**
   * Number to add to the {@link #column logical column} in order to get folding-introduced visual column offset.
   *
   * @see #visualPositionAware
   */
  public final int foldingColumnDiff;

  public LogicalPosition(int line, int column) {
    this(line, column, 0, 0, 0, 0, 0, false);
  }

  public LogicalPosition(int line, int column, int softWrapLines, int linesFromActiveSoftWrap, int softWrapColumnDiff,
                         int foldedLines, int foldingColumnDiff)
  {
    this(line, column, softWrapLines, linesFromActiveSoftWrap, softWrapColumnDiff, foldedLines, foldingColumnDiff, true);
  }

  private LogicalPosition(int line, int column, int softWrapLines, int linesFromActiveSoftWrap, int softWrapColumnDiff, int foldedLines,
                          int foldingColumnDiff, boolean visualPositionAware)
  {
    assert linesFromActiveSoftWrap <= softWrapLines;

    this.line = line;
    this.column = column;
    this.softWrapLines = softWrapLines;
    this.linesFromActiveSoftWrap = linesFromActiveSoftWrap;
    this.softWrapColumnDiff = softWrapColumnDiff;
    this.foldedLines = foldedLines;
    this.foldingColumnDiff = foldingColumnDiff;
    this.visualPositionAware = visualPositionAware;
  }

  /**
   * Allows to answer if current position points to soft wrap-introduced visual line.
   *
   * @return    <code>true</code> if current position points to soft wrap-introduced visual line; <code>false</code> otherwise
   */
  public boolean isOnSoftWrappedLine() {
    return softWrapColumnDiff != 0;
  }

  /**
   * Builds visual position based on a state of the current logical position.
   * <p/>
   * Such visual position is considered to make sense only if current logical position
   * is {@link #visualPositionAware visual position aware}.
   *
   * @return    visual position based on a state of the current logical position
   */
  public VisualPosition toVisualPosition() {
    return new VisualPosition(line + softWrapLines - foldedLines, column + softWrapColumnDiff + foldingColumnDiff);
  }

  public boolean equals(Object o) {
    if (!(o instanceof LogicalPosition)) return false;
    final LogicalPosition logicalPosition = (LogicalPosition) o;

    return column == logicalPosition.column && line == logicalPosition.line;
  }

  public int hashCode() {
    return 29 * line + column;
  }

  @NonNls
  public String toString() {
    return "LogicalPosition: line=" + line + " column=" + column + "; visual position aware=" + visualPositionAware
           + " soft wrap: lines=" + softWrapLines + " (active=" + linesFromActiveSoftWrap + ") columns diff=" + softWrapColumnDiff
           + "; folding: lines = " + foldedLines + " columns diff=" + foldingColumnDiff;
  }

  public int compareTo(LogicalPosition position) {
    if (line != position.line) return line - position.line;
    if (column != position.column) return column - position.column;
    if (softWrapLines != position.softWrapLines) return softWrapLines - position.softWrapLines;
    return softWrapColumnDiff - position.softWrapColumnDiff;
  }
}

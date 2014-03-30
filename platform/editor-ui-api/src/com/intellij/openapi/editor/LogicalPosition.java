/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import java.awt.Point;

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
 * @see Editor#xyToLogicalPosition(Point)
 */
public class LogicalPosition implements Comparable<LogicalPosition> {
  public final int line;
  public final int column;

  /**
   * Identifies if current logical position may be correctly mapped to visual position. E.g. we can define properties like
   * {@link #softWrapLinesBeforeCurrentLogicalLine}, {@link #softWrapColumnDiff} etc during {@code 'visual position' -> 'logical position'} conversion
   * in order to be able to easy match it back to visual position.
   */
  public final boolean visualPositionAware;

  /**
   * Number of virtual soft wrap-introduced lines before the current logical line.
   *
   * @see #visualPositionAware
   */
  public final int softWrapLinesBeforeCurrentLogicalLine;

  /**
   * Number of virtual soft wrap introduced lines on a current logical line before the visual position that corresponds
   * to the current logical position.
   *
   * @see #visualPositionAware
   */
  public final int softWrapLinesOnCurrentLogicalLine;

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

  public LogicalPosition(int line, int column) throws IllegalArgumentException {
    this(line, column, 0, 0, 0, 0, 0, false);
  }

  public LogicalPosition(int line, int column, int softWrapLinesBeforeCurrentLogicalLine, int softWrapLinesOnCurrentLogicalLine,
                         int softWrapColumnDiff, int foldedLines, int foldingColumnDiff) throws IllegalArgumentException
  {
    this(line, column, softWrapLinesBeforeCurrentLogicalLine, softWrapLinesOnCurrentLogicalLine, softWrapColumnDiff, foldedLines,
      foldingColumnDiff, true);
  }

  private LogicalPosition(int line, int column, int softWrapLinesBeforeCurrentLogicalLine, int softWrapLinesOnCurrentLogicalLine,
                          int softWrapColumnDiff, int foldedLines, int foldingColumnDiff, boolean visualPositionAware)
    throws IllegalArgumentException {
    if (column + softWrapColumnDiff + foldingColumnDiff < 0) {
      throw new IllegalArgumentException(String.format(
        "Attempt to create %s with invalid arguments - resulting column is negative (%d). Given arguments: line=%d, column=%d, "
        + "soft wrap lines before: %d, soft wrap lines current: %d, soft wrap column diff: %d, folded lines: %d, folding column "
        + "diff: %d, visual position aware: %b",
        getClass().getName(), column + softWrapColumnDiff + foldingColumnDiff, line, column, softWrapLinesBeforeCurrentLogicalLine,
        softWrapLinesOnCurrentLogicalLine, softWrapColumnDiff, foldedLines, foldingColumnDiff, visualPositionAware
      ));
    }
    if (line < 0) throw new IllegalArgumentException("line must be non negative: "+line);
    if (column < 0) throw new IllegalArgumentException("column must be non negative: "+column);
    this.line = line;
    this.column = column;
    this.softWrapLinesBeforeCurrentLogicalLine = softWrapLinesBeforeCurrentLogicalLine;
    this.softWrapLinesOnCurrentLogicalLine = softWrapLinesOnCurrentLogicalLine;
    this.softWrapColumnDiff = softWrapColumnDiff;
    this.foldedLines = foldedLines;
    this.foldingColumnDiff = foldingColumnDiff;
    this.visualPositionAware = visualPositionAware;
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
    return new VisualPosition(
      line + softWrapLinesBeforeCurrentLogicalLine + softWrapLinesOnCurrentLogicalLine - foldedLines,
      column + softWrapColumnDiff + foldingColumnDiff
    );
  }

  /**
   * Returns a new instance of class corresponding to the same logical position in the document, but without any cached
   * reference to its visual position.
   */
  public LogicalPosition withoutVisualPositionInfo() {
    return new LogicalPosition(line, column);
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
    return "LogicalPosition: (" + line + ", " + column + ")"
           + (visualPositionAware ? "; vp aware" : "")
           + (softWrapLinesBeforeCurrentLogicalLine + softWrapLinesOnCurrentLogicalLine == 0
              ? ""
              : "; soft wrap: lines=" + (softWrapLinesBeforeCurrentLogicalLine + softWrapLinesOnCurrentLogicalLine)
                + " (before=" + softWrapLinesBeforeCurrentLogicalLine + "; current=" + softWrapLinesOnCurrentLogicalLine + ")")
           + (softWrapColumnDiff == 0 ? "" : "; columns diff=" + softWrapColumnDiff + ";" )
           + (foldedLines == 0? "" : "; folding: lines = " + foldedLines + ";")
           + (foldingColumnDiff == 0 ? "" : "; columns diff=" + foldingColumnDiff);
  }

  public int compareTo(LogicalPosition position) {
    if (line != position.line) return line - position.line;
    if (column != position.column) return column - position.column;
    if (softWrapLinesBeforeCurrentLogicalLine != position.softWrapLinesBeforeCurrentLogicalLine) return softWrapLinesBeforeCurrentLogicalLine - position.softWrapLinesBeforeCurrentLogicalLine;
    return softWrapColumnDiff - position.softWrapColumnDiff;
  }
}

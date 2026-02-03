// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.awt.*;

/**
 * Represents a logical position in the editor, including line and column values (both zero-based). Line value relates to the corresponding
 * line in the editor's {@link Document}. Column value counts characters from the beginning of the logical line (tab character can occupy
 * multiple columns - up to the tab size set for editor, surrogate pairs of characters are counted as one column). Positions beyond the end
 * of line can be represented (in this case column number will be larger than the number of characters in the line).
 * <p>
 * Logical position corresponds to a boundary between two characters and can be associated with either a preceding or succeeding character
 * (see {@link #leansForward}). This association makes a difference in a bidirectional text, where a mapping from logical to visual position 
 * is not continuous.
 * <p>
 * Logical position of caret in current editor is displayed in IDE's status bar (displayed line and column values are one-based, so they are
 * incremented before showing).
 * <p>
 * <b>Note:</b> two objects of this class are considered equal if their logical line and column are equal. I.e. all logical positions
 * for soft wrap-introduced virtual space and the first document symbol after soft wrap are considered to be equal. Value of 
 * {@link #leansForward} flag doesn't impact the equality of logical positions.
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
   * If {@code true}, this position is associated with succeeding character (in logical order), otherwise it's associated with
   * preceding character. This can make difference in bidirectional text, where logical positions which differ only in this flag's value
   * can have different visual positions.
   * <p>
   * This field has no impact on equality and comparison relationships between {@code LogicalPosition} instances.
   */
  public final boolean leansForward;

  public LogicalPosition(int line, int column) throws IllegalArgumentException {
    this(line, column, false);
  }

  public LogicalPosition(int line, int column, boolean leansForward) throws IllegalArgumentException {
    if (line < 0) throw new IllegalArgumentException("line must be non negative: "+line);
    if (column < 0) throw new IllegalArgumentException("column must be non negative: "+column);
    this.line = line;
    this.column = column;
    this.leansForward = leansForward;
  }

  @VisibleForTesting
  public int getLine() {
    return line;
  }

  @VisibleForTesting
  public int getColumn() {
    return column;
  }

  /**
   * Constructs a new {@code LogicalPosition} instance with a given value of {@link #leansForward} flag.
   */
  public LogicalPosition leanForward(boolean value) {
    return new LogicalPosition(line, column, value);
  }
  
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof LogicalPosition logicalPosition)) return false;

    return column == logicalPosition.column && line == logicalPosition.line;
  }

  @Override
  public int hashCode() {
    return 29 * line + column;
  }

  @Override
  public @NonNls String toString() {
    return "LogicalPosition: (" + line + ", " + column + ")"
           + (leansForward ? "; leans forward" : "");
  }

  @Override
  public int compareTo(@NotNull LogicalPosition position) {
    if (line != position.line) return line - position.line;
    return column - position.column;
  }
}

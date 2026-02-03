// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a 0-based visual position in the editor.
 * <p>
 * Visual positions take folding into account.
 * For example, if the top 10 lines of the document are folded into a single line,
 * the logical lines 0 to 9 all have the visual line 0,
 * and the logical line 10 has the visual line 1.
 * <p>
 * A visual position corresponds to a boundary between two characters
 * and can be associated with either a preceding or succeeding character (see {@link #leansRight}).
 * This association makes a difference in a bidirectional text,
 * where a mapping from visual to logical position is not continuous.
 *
 * @see LogicalPosition
 * @see Editor#logicalToVisualPosition(LogicalPosition)
 * @see Editor#offsetToVisualPosition(int)
 * @see Editor#xyToVisualPosition(java.awt.Point)
 */
public final class VisualPosition {
  public final int line;
  public final int column;
  /**
   * If {@code true}, this position is associated with succeeding character (in visual order), otherwise it's associated with
   * preceding character. This can make difference in bidirectional text, where visual positions which differ only in this flag's value
   * can correspond to a different logical positions.
   * <p>
   * This field has no impact on equality and comparison relationships between {@code VisualPosition} instances.
   */
  public final boolean leansRight;

  public VisualPosition(int line, int column) {
    this(line, column, false);
  }

  public VisualPosition(int line, int column, boolean leansRight) {
    if (line < 0) throw new IllegalArgumentException("line must be non negative: " + line);
    if (column < 0) throw new IllegalArgumentException("column must be non negative: " + column);
    this.line = line;
    this.column = column;
    this.leansRight = leansRight;
  }

  /**
   * Allows to answer if current visual position is located after the given one.
   * <p/>
   * One visual position is considered to be 'after' another only if one of the following is true:
   * <pre>
   * <ul>
   *   <li>its visual line is greater;</li>
   *   <li>it has the same visual line but its column is greater;</li>
   * </ul>
   * </pre>
   *
   * @param other visual position to compare with the current one
   * @return {@code true} if current position is 'after' the given one; {@code false} otherwise
   */
  public boolean after(@NotNull VisualPosition other) {
    if (line == other.line) {
      return column > other.column;
    }
    return line > other.line;
  }

  /**
   * Constructs a new {@code VisualPosition} instance with a given value of {@link #leansRight} flag.
   */
  public VisualPosition leanRight(boolean value) {
    return new VisualPosition(line, column, value);
  }

  @Override
  public @NonNls String toString() {
    return "VisualPosition: (" + line + ", " + column + ")" + (leansRight ? " leans right" : "");
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof VisualPosition that)) return false;

    if (column != that.column) return false;
    if (line != that.line) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = line;
    result = 31 * result + column;
    return result;
  }

  /** @return the 0-based visual line number */
  public int getLine() {
    return line;
  }

  /** @return the 0-based visual column number */
  public int getColumn() {
    return column;
  }
}

/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;

/**
 * Represents a visual position in the editor. Visual positions take folding into account -
 * for example, if the top 10 lines of the document are folded, the 10th line in the document
 * will have the line number 1 in its visual position.
 * <p>
 * Visual position corresponds to a boundary between two characters and can be associated with either a preceding or succeeding character 
 * (see {@link #leansRight}). This association makes a difference in a bidirectional text, where a mapping from visual to logical position 
 * is not continuous.
 *
 * @see LogicalPosition
 * @see Editor#logicalToVisualPosition(LogicalPosition)
 * @see Editor#offsetToVisualPosition(int)
 * @see Editor#xyToVisualPosition(java.awt.Point)
 */
public class VisualPosition {
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
    if (line < 0) throw new IllegalArgumentException("line must be non negative: "+line);
    if (column < 0) throw new IllegalArgumentException("column must be non negative: "+column);
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
   * @param other   visual position to compare with the current one
   * @return        {@code true} if current position is 'after' the given one; {@code false} otherwise
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

  @NonNls
  public String toString() {
    return "VisualPosition: (" + line + ", " + column+")" + (leansRight ? " leans right" : "");
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof VisualPosition)) return false;

    final VisualPosition that = (VisualPosition)o;

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

  public int getLine() {
    return line;
  }

  public int getColumn() {
    return column;
  }
}

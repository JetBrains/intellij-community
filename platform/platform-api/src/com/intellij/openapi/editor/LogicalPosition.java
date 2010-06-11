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
 * //TODO den add doc equals/compareTo() behavior difference because of soft wraps.
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
  //TODO den add doc
  public final int softWrapLines;
  //TODO den add doc
  public final int linesFromActiveSoftWrap;
  //TODO den add doc
  public final int softWrapColumns;

  public LogicalPosition(int line, int column) {
    this(line, column, 0, 0, 0);
  }

  public LogicalPosition(int line, int column, int softWrapLines, int linesFromActiveSoftWrap, int softWrapColumns) {
    assert linesFromActiveSoftWrap <= softWrapLines;

    this.line = line;
    this.column = column;
    this.softWrapLines = softWrapLines;
    this.linesFromActiveSoftWrap = linesFromActiveSoftWrap;
    this.softWrapColumns = softWrapColumns;
  }

  //TODO den add doc
  public boolean isOnSoftWrappedLine() {
    return softWrapColumns != 0;
  }

  public boolean equals(Object o) {
    if (!(o instanceof LogicalPosition)) return false;
    final LogicalPosition logicalPosition = (LogicalPosition) o;

    return column == logicalPosition.column && line == logicalPosition.line && softWrapLines == logicalPosition.softWrapLines
           && softWrapColumns == logicalPosition.softWrapColumns;
  }

  public int hashCode() {
    int result = 29 * line + column;
    result = result * 29 + softWrapLines;
    return 29 * result + softWrapColumns;
  }

  @NonNls
  public String toString() {
    return "LogicalPosition: line = " + line + " column = " + column + "; soft wrap: lines = " + softWrapLines
           + " (active = " + linesFromActiveSoftWrap + ") columns = " + softWrapColumns;
  }

  public int compareTo(LogicalPosition position) {
    if (line != position.line) return line - position.line;
    if (column != position.column) return column - position.column;
    if (softWrapLines != position.softWrapLines) return softWrapLines - position.softWrapLines;
    return softWrapColumns - position.softWrapColumns;
  }
}

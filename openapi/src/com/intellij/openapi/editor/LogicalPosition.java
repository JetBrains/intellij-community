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
package com.intellij.openapi.editor;

import org.jetbrains.annotations.NonNls;

/**
 * Represents a logical position in the editor. Logical positions ignore folding -
 * for example, if the top 10 lines of the document are folded, the 10th line in the document
 * will have the line number 10 in its logical position.
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

  public LogicalPosition(int line, int column) {
    this.line = line;
    this.column = column;
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
    return "LogicalPosition: line = " + line + " column = " + column;
  }

  public int compareTo(LogicalPosition position) {
    if (line != position.line) return line - position.line;
    return column - position.column;
  }
}

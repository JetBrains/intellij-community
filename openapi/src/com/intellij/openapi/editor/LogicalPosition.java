/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor;

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

  public String toString() {
    return "LogicalPosition: line = " + line + " column = " + column;
  }

  public int compareTo(LogicalPosition position) {
    if (line != position.line) return line - position.line;
    return column - position.column;
  }
}

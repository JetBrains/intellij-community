/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

/*
 * @author max
 */
package com.intellij.openapi.editor;

public class IndentGuideDescriptor {
  public final int indentLevel;
  public final int codeConstructStartLine;
  public final int startLine;
  public final int endLine;

  public IndentGuideDescriptor(int indentLevel, int startLine, int endLine) {
    this(indentLevel, startLine, startLine, endLine);
  }

  public IndentGuideDescriptor(int indentLevel, int codeConstructStartLine, int startLine, int endLine) {
    this.indentLevel = indentLevel;
    this.codeConstructStartLine = codeConstructStartLine;
    this.startLine = startLine;
    this.endLine = endLine;
  }

  @Override
  public int hashCode() {
    int result = indentLevel;
    result = 31 * result + startLine;
    result = 31 * result + endLine;
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    IndentGuideDescriptor that = (IndentGuideDescriptor)o;

    if (endLine != that.endLine) return false;
    if (indentLevel != that.indentLevel) return false;
    if (startLine != that.startLine) return false;

    return true;
  }

  @Override
  public String toString() {
    return String.format("%d (%d-%d-%d)", indentLevel, codeConstructStartLine, startLine, endLine);
  }
}

// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build;

import java.io.File;
import java.util.Objects;

/**
 * 0-based position in file.
 *
 * @author Vladislav.Soroka
 */
public class FilePosition {
  private final File myFile;
  private final int myStartLine;
  private final int myStartColumn;
  private final int myEndLine;
  private final int myEndColumn;

  /**
   * @param file file
   * @param line 0-based line number
   * @param column 0-based column number
   */
  public FilePosition(File file, int line, int column) {
    this(file, line, column, line, column);
  }

  /**
   *
   * @param file file
   * @param startLine 0-based start line number
   * @param startColumn 0-based start column number
   * @param endLine 0-based end number
   * @param endColumn 0-based end column number
   */
  public FilePosition(File file, int startLine, int startColumn, int endLine, int endColumn) {
    myFile = file;
    myStartLine = startLine;
    myStartColumn = startColumn;
    myEndLine = endLine;
    myEndColumn = endColumn;
  }

  public File getFile() {
    return myFile;
  }

  public int getStartLine() {
    return myStartLine;
  }

  public int getStartColumn() {
    return myStartColumn;
  }

  public int getEndLine() {
    return myEndLine;
  }

  public int getEndColumn() {
    return myEndColumn;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    FilePosition position = (FilePosition)o;
    return myStartLine == position.myStartLine &&
           myStartColumn == position.myStartColumn &&
           myEndLine == position.myEndLine &&
           myEndColumn == position.myEndColumn &&
           Objects.equals(myFile, position.myFile);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myFile, myStartLine, myStartColumn, myEndLine, myEndColumn);
  }
}

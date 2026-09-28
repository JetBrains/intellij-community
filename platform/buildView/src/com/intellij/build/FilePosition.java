// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.Objects;

/**
 * 0-based position in a file.
 *
 * @author Vladislav.Soroka
 */
public final class FilePosition {
  private final Path myFile;
  private final int myStartLine;
  private final int myStartColumn;
  private final int myEndLine;
  private final int myEndColumn;

  /**
   * @param file file
   * @param line 0-based line number
   * @param column 0-based column number
   */
  public FilePosition(Path file, int line, int column) {
    this(file, line, column, line, column);
  }

  /**
   * @param file file
   * @param line 0-based line number
   * @param column 0-based column number
   * @deprecated Use a constructor which accepts a {@link Path} instance.
   */
  @Deprecated(since = "2026.2", forRemoval = true)
  public FilePosition(File file, int line, int column) {
    this(file.toPath(), line, column);
  }

  /**
   *
   * @param file file
   * @param startLine 0-based start line number
   * @param startColumn 0-based start column number
   * @param endLine 0-based end line number
   * @param endColumn 0-based end column number
   */
  public FilePosition(Path file, int startLine, int startColumn, int endLine, int endColumn) {
    myFile = file;
    myStartLine = startLine;
    myStartColumn = startColumn;
    myEndLine = endLine;
    myEndColumn = endColumn;
  }

  /**
   *
   * @param file file
   * @param startLine 0-based start line number
   * @param startColumn 0-based start column number
   * @param endLine 0-based end number
   * @param endColumn 0-based end column number
   * @deprecated Use a constructor which accepts a {@link Path} instance.
   */
  @Deprecated(since = "2026.2", forRemoval = true)
  public FilePosition(File file, int startLine, int startColumn, int endLine, int endColumn) {
    this(file.toPath(), startLine, startColumn, endLine, endColumn);
  }

  public @Nullable Path getPath() {
    return myFile;
  }

  /**
   * @deprecated Use {@link getPath}.
   */
  @Deprecated(since = "2026.2", forRemoval = true)
  public @Nullable File getFile() {
    return getPath().toFile();
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

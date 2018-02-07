/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.incremental.messages;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;

/**
 * @author Eugene Zhuravlev
 */
public class CompilerMessage extends BuildMessage {

  private final String myCompilerName;
  private final long myProblemBeginOffset;
  private final long myProblemEndOffset;
  private final long myProblemLocationOffset;
  private final String mySourcePath;
  private final long myLine;
  private final long myColumn;

  public CompilerMessage(@NotNull String compilerName, @NotNull Throwable internalError) {
    this(compilerName, Kind.ERROR, getTextFromThrowable(internalError), null, -1L, -1L, -1L, -1L, -1L);
  }

  public CompilerMessage(@NotNull String compilerName, Kind kind, String messageText) {
    this(compilerName, kind, messageText, null, -1L, -1L, -1L, -1L, -1L);
  }

  public CompilerMessage(@NotNull String compilerName, Kind kind, String messageText, String sourcePath) {
    this(compilerName, kind, messageText, sourcePath, -1L, -1L, -1L, -1L, -1L);
  }

  public CompilerMessage(@NotNull String compilerName, Kind kind, String messageText,
                         @Nullable String sourcePath,
                         long problemBeginOffset,
                         long problemEndOffset,
                         long problemLocationOffset,
                         long locationLine,
                         long locationColumn) {
    super(messageText, kind);
    myCompilerName = compilerName;
    myProblemBeginOffset = problemBeginOffset;
    myProblemEndOffset = problemEndOffset;
    myProblemLocationOffset = problemLocationOffset;
    mySourcePath = sourcePath != null && !sourcePath.isEmpty()? sourcePath.replace(File.separatorChar, '/') : null;
    myLine = locationLine;
    myColumn = locationColumn;
  }

  @NotNull
  public String getCompilerName() {
    return myCompilerName;
  }

  @Nullable
  public String getSourcePath() {
    return mySourcePath;
  }

  public long getLine() {
    return myLine;
  }

  public long getColumn() {
    return myColumn;
  }

  public long getProblemBeginOffset() {
    return myProblemBeginOffset;
  }

  public long getProblemEndOffset() {
    return myProblemEndOffset;
  }

  public long getProblemLocationOffset() {
    return myProblemLocationOffset;
  }

  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append(getCompilerName()).append(":").append(getKind().name()).append(":").append(super.toString());
    final String path = getSourcePath();
    if (path != null) {
      builder.append("; file: ").append(path);
      final long line = getLine();
      final long column = getColumn();
      if (line >= 0 && column >= 0) {
        builder.append(" at (").append(line).append(":").append(column).append(")");
      }
    }
    return builder.toString();
  }

  public static String getTextFromThrowable(Throwable internalError) {
    StringBuilder text = new StringBuilder();
    text.append("Error: ");
    final String msg = internalError.getMessage();
    if (!StringUtil.isEmptyOrSpaces(msg)) {
      text.append(msg);
    }
    else {
      text.append(internalError.getClass().getName());
    }
    text.append("\n");

    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    internalError.printStackTrace(new PrintStream(out));
    text.append(out.toString());

    return text.toString();
  }

}

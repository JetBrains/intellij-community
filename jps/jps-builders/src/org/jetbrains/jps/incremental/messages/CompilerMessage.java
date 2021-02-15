// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.messages;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

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
  private final Collection<String> myModuleNames = new HashSet<>();

  /**
   * @deprecated use either {@link #createInternalCompilationError(String, Throwable)} or {@link #createInternalBuilderError(String, Throwable)} instead
   */
  @Deprecated
  public CompilerMessage(@NlsSafe @NotNull String compilerName, @NotNull Throwable internalError) {
    this(compilerName, Kind.ERROR, getTextFromThrowable(internalError), null, -1L, -1L, -1L, -1L, -1L);
  }

  public CompilerMessage(@NlsSafe @NotNull String compilerName, Kind kind, @Nls(capitalization = Nls.Capitalization.Sentence) String messageText) {
    this(compilerName, kind, messageText, null, -1L, -1L, -1L, -1L, -1L);
  }

  public CompilerMessage(@NlsSafe @NotNull String compilerName, Kind kind, @Nls(capitalization = Nls.Capitalization.Sentence) String messageText, String sourcePath) {
    this(compilerName, kind, messageText, sourcePath, -1L, -1L, -1L, -1L, -1L);
  }

  public CompilerMessage(@NlsSafe @NotNull String compilerName, Kind kind, @Nls(capitalization = Nls.Capitalization.Sentence) String messageText,
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

  public void addModuleName(String moduleName) {
    myModuleNames.add(moduleName);
  }

  public Collection<String> getModuleNames() {
    return Collections.unmodifiableCollection(myModuleNames);
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


  /**
   * Return a message describing an exception in the underlying compiler. Such messages will be reported as compilation errors.
   */
  public static CompilerMessage createInternalCompilationError(@Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String compilerName,
                                                               @NotNull Throwable t) {
    return new CompilerMessage(compilerName, t);
  }

  /**
   * Return a message describing an error in JPS builders code. Such messages will be reported as regular compilation errors and also will be logged
   * as fatal errors of the IDE.
   */
  public static CompilerMessage createInternalBuilderError(@Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String compilerName,
                                                           @NotNull Throwable t) {
    return new CompilerMessage(compilerName, Kind.INTERNAL_BUILDER_ERROR, getTextFromThrowable(t));
  }

  @Override
  public @Nls String getMessageText() {
    //noinspection HardCodedStringLiteral
    return super.getMessageText();
  }

  public static @NlsSafe String getTextFromThrowable(Throwable t) {
    String message = t.getMessage();
    if (StringUtil.isEmptyOrSpaces(message)) {
      message = t.getClass().getName();
    }

    StringWriter writer = new StringWriter();
    t.printStackTrace(new PrintWriter(writer));

    return "Error: " + message + '\n' + writer.getBuffer();
  }
}
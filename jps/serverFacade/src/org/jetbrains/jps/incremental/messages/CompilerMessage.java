package org.jetbrains.jps.incremental.messages;

import com.sun.istack.internal.Nullable;

import java.io.File;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/29/11
 */
public class CompilerMessage extends BuildMessage {

  private final String myCompilerName;
  private final long myProblemBeginOffset;
  private final long myProblemEndOffset;
  private final long myProblemLocationOffset;
  private final String mySourcePath;
  private final long myLine;
  private final long myColumn;

  public CompilerMessage(String compilerName, Kind kind, String messageText) {
    this(compilerName, kind, messageText, null, -1L, -1L, -1L, -1L, -1L);
  }

  public CompilerMessage(String compilerName, Kind kind, String messageText, String sourcePath) {
    this(compilerName, kind, messageText, sourcePath, -1L, -1L, -1L, -1L, -1L);
  }

  public CompilerMessage(String compilerName, Kind kind, String messageText,
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
    mySourcePath = sourcePath != null? sourcePath.replace(File.separatorChar, '/') : null;
    myLine = locationLine;
    myColumn = locationColumn;
  }

  public String getCompilerName() {
    return myCompilerName;
  }

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
    return getCompilerName() + ":" + getKind().name() + ":" + super.toString();
  }
}

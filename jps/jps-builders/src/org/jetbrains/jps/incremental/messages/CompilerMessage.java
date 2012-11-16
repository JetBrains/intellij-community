package org.jetbrains.jps.incremental.messages;

import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;

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

  public CompilerMessage(String compilerName, Throwable internalError) {
    this(compilerName, Kind.ERROR, getTextFromThrowable(internalError), null, -1L, -1L, -1L, -1L, -1L);
  }

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
    mySourcePath = sourcePath != null && !sourcePath.isEmpty()? sourcePath.replace(File.separatorChar, '/') : null;
    myLine = locationLine;
    myColumn = locationColumn;
  }

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
    return getCompilerName() + ":" + getKind().name() + ":" + super.toString();
  }

  private static String getTextFromThrowable(Throwable internalError) {
    StringBuilder text = new StringBuilder();
    text.append("Error: ");
    final String msg = internalError.getMessage();
    if (msg != null) {
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

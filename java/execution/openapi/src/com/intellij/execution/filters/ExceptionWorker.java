// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.filters;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ExceptionWorker {
  @NonNls private static final String AT = "at";
  private static final String AT_PREFIX = AT + " ";
  private static final String STANDALONE_AT = " " + AT + " ";

  private final ExceptionLineParser myParser;

  public ExceptionWorker(@NotNull ExceptionInfoCache cache) {
    myParser = ExceptionLineParserFactory.getInstance().create(cache);
  }

  public Filter.Result execute(@NotNull String line, final int textEndOffset) {
    return myParser.execute(line, textEndOffset);
  }

  private static int getLineNumber(@NotNull String lineString) {
    // some quick checks to avoid costly exceptions
    if (lineString.isEmpty() || lineString.length() > 9 || !Character.isDigit(lineString.charAt(0))) {
      return -1;
    }

    try {
      return Integer.parseInt(lineString);
    }
    catch (NumberFormatException e) {
      return -1;
    }
  }

  public Filter.Result getResult() {
    return myParser.getResult();
  }

  public String getMethod() {
    return myParser.getMethod();
  }

  public PsiFile getFile() {
    return myParser.getFile();
  }

  public ParsedLine getInfo() {
    return myParser.getInfo();
  }

  private static int findAtPrefix(@NotNull String line) {
    if (line.startsWith(AT_PREFIX)) return 0;

    int startIdx = line.indexOf(STANDALONE_AT);
    return startIdx < 0 ? line.indexOf(AT_PREFIX) : startIdx;
  }

  /**
   * Returns the location of the leftmost closing bracket following a digit: '\d)'
   * If there is no such pattern in the line, but there is a single closing bracket, then returns its location.
   * If there are no closing brackets at all, or more than one closing bracket (but none of them following a digit), then returns -1.
   */
  private static int findRParenAfterLocation(@NotNull String line, int startIdx) {
    int singleRParen = line.indexOf(')', startIdx);
    int next = singleRParen;
    while (next > -1) {
      if (next >= 1 && Character.isDigit(line.charAt(next - 1))) {
        return next;
      }
      next = line.indexOf(')', next + 1);
      if (next != -1) { // found a second closing bracket
        singleRParen = -1;
      }
    }
    return singleRParen;
  }

  @Nullable
  public static ParsedLine parseExceptionLine(@NotNull String line) {
    ParsedLine result = parseNormalStackTraceLine(line);
    if (result == null) result = parseYourKitLine(line);
    if (result == null) result = parseForcedLine(line);
    if (result == null) result = parseLinchekLine(line);
    return result;
  }

  @Nullable
  private static ParsedLine parseNormalStackTraceLine(@NotNull String line) {
    return parseStackTraceLine(line, false);
  }

  @Nullable
  private static ParsedLine parseLinchekLine(@NotNull String line) {
    if (line.startsWith("|")) {
      return parseStackTraceLine(line, true);
    }
    return null;
  }

  @Nullable
  private static ParsedLine parseStackTraceLine(@NotNull String line, boolean searchForRParenOnlyAfterAt) {
    int startIdx = findAtPrefix(line);
    int rParenIdx = findRParenAfterLocation(line, searchForRParenOnlyAfterAt  ? startIdx : 0);
    if (rParenIdx < 0) return null;

    TextRange methodName = findMethodNameCandidateBefore(line, startIdx, rParenIdx);
    if (methodName == null) return null;

    int lParenIdx = methodName.getEndOffset();
    int dotIdx = methodName.getStartOffset() - 1;
    int moduleIdx = line.indexOf('/');
    int classNameIdx;
    if (moduleIdx > -1 && moduleIdx < dotIdx && !line.startsWith("0x", moduleIdx + 1)) {
      classNameIdx = moduleIdx + 1;
      // `//` is used as a separator in an unnamed module with a class loader name
      if (line.charAt(classNameIdx) == '/') {
        classNameIdx++;
      }
    }
    else {
      if (startIdx >= 0) {
        // consider STANDALONE_AT here
        classNameIdx = startIdx + 1 + AT.length() + (line.charAt(startIdx) == 'a' ? 0 : 1);
      } else {
        classNameIdx = 0;
      }
    }

    return ParsedLine.createFromFileAndLine(new TextRange(classNameIdx, handleSpaces(line, dotIdx, -1)),
                                            trimRange(line, methodName),
                                            lParenIdx + 1, rParenIdx, line);
  }

  @NotNull
  private static TextRange trimRange(@NotNull String line, @NotNull TextRange range) {
    int start = handleSpaces(line, range.getStartOffset(), 1);
    int end = handleSpaces(line, range.getEndOffset(), -1);
    if (start != range.getStartOffset() || end != range.getEndOffset()) {
      return TextRange.create(start, end);
    }
    return range;
  }

  @Nullable
  private static ParsedLine parseYourKitLine(@NotNull String line) {
    int lineEnd = line.length() - 1;
    if (lineEnd > 0 && line.charAt(lineEnd) == '\n') lineEnd--;
    if (lineEnd > 0 && Character.isDigit(line.charAt(lineEnd))) {
      int spaceIndex = line.lastIndexOf(' ');
      int rParenIdx = line.lastIndexOf(')');
      if (rParenIdx > 0 && spaceIndex == rParenIdx + 1) {
        TextRange methodName = findMethodNameCandidateBefore(line, 0, rParenIdx);
        if (methodName != null) {
          return ParsedLine.createFromFileAndLine(new TextRange(0, methodName.getStartOffset() - 1),
                                                  methodName,
                                                  spaceIndex + 1, lineEnd + 1,
                                                  line);
        }
      }
    }
    return null;
  }

  @Nullable
  private static ParsedLine parseForcedLine(@NotNull String line) {
    String dash = "- ";
    if (!line.trim().startsWith(dash)) return null;

    String linePrefix = "line=";
    int lineNumberStart = line.indexOf(linePrefix);
    if (lineNumberStart < 0) return null;

    int lineNumberEnd = line.indexOf(' ', lineNumberStart);
    if (lineNumberEnd < 0) return null;

    TextRange methodName = findMethodNameCandidateBefore(line, 0, lineNumberStart);
    if (methodName == null) return null;

    int lineNumber = getLineNumber(line.substring(lineNumberStart + linePrefix.length(), lineNumberEnd));
    if (lineNumber < 0) return null;

    return new ParsedLine(trimRange(line, TextRange.create(line.indexOf(dash) + dash.length(), methodName.getStartOffset() - 1)),
                          methodName,
                          TextRange.create(lineNumberStart, lineNumberEnd), null, lineNumber);
  }

  private static TextRange findMethodNameCandidateBefore(@NotNull String line, int start, int end) {
    int lParenIdx = line.lastIndexOf('(', end);
    if (lParenIdx < 0) return null;

    int dotIdx = line.lastIndexOf('.', lParenIdx);
    if (dotIdx < 0 || dotIdx < start) return null;

    return TextRange.create(dotIdx + 1, lParenIdx);
  }

  private static int handleSpaces(@NotNull String line, int pos, int delta) {
    int len = line.length();
    while (pos >= 0 && pos < len) {
      final char c = line.charAt(pos);
      if (!Character.isSpaceChar(c)) break;
      pos += delta;
    }
    return pos;
  }

  public static class ParsedLine {
    @NotNull public final TextRange classFqnRange;
    @NotNull public final TextRange methodNameRange;
    @NotNull public final TextRange fileLineRange;
    @Nullable public final String fileName;
    public final int lineNumber;

    ParsedLine(@NotNull TextRange classFqnRange,
               @NotNull TextRange methodNameRange,
               @NotNull TextRange fileLineRange, @Nullable String fileName, int lineNumber) {
      this.classFqnRange = classFqnRange;
      this.methodNameRange = methodNameRange;
      this.fileLineRange = fileLineRange;
      this.fileName = fileName;
      this.lineNumber = lineNumber;
    }

    @Nullable
    private static ParsedLine createFromFileAndLine(@NotNull TextRange classFqnRange,
                                                    @NotNull TextRange methodNameRange,
                                                    int fileLineStart, int fileLineEnd, @NotNull String line) {
      TextRange fileLineRange = TextRange.create(fileLineStart, fileLineEnd);
      String fileAndLine = fileLineRange.substring(line);

      if ("Native Method".equals(fileAndLine) || "Unknown Source".equals(fileAndLine)) {
        return new ParsedLine(classFqnRange, methodNameRange, fileLineRange, null, -1);
      }

      int colonIndex = fileAndLine.lastIndexOf(':');
      if (colonIndex < 0) return null;

      int lineNumber = getLineNumber(fileAndLine.substring(colonIndex + 1));
      if (lineNumber < 0) return null;

      return new ParsedLine(classFqnRange, methodNameRange, fileLineRange, fileAndLine.substring(0, colonIndex).trim(), lineNumber);
    }
  }
}

/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.execution.filters;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Yura Cangea
 * @version 1.0
 */
public class RegexpFilter implements Filter, DumbAware {
  @NonNls public static final String FILE_PATH_MACROS = "$FILE_PATH$";
  @NonNls public static final String LINE_MACROS = "$LINE$";
  @NonNls public static final String COLUMN_MACROS = "$COLUMN$";

  @NonNls private static final String FILE_PATH_REGEXP = "((?:\\p{Alpha}\\:)?[0-9 a-z_A-Z\\-\\\\./]+)";
  @NonNls private static final String NUMBER_REGEXP = "([0-9]+)";
  @NonNls private static final String FILE_STR = "file";
  @NonNls private static final String LINE_STR = "line";
  @NonNls private static final String COLUMN_STR = "column";

  private final int myFileRegister;
  private final int myLineRegister;
  private final int myColumnRegister;

  private final Pattern myPattern;
  private final Project myProject;

  public RegexpFilter(Project project, @NonNls String expression) {
    myProject = project;
    validate(expression);

    if (expression.trim().isEmpty()) {
      throw new InvalidExpressionException("expression == null or empty");
    }

    int filePathIndex = expression.indexOf(FILE_PATH_MACROS);
    int lineIndex = expression.indexOf(LINE_MACROS);
    int columnIndex = expression.indexOf(COLUMN_MACROS);

    if (filePathIndex == -1) {
      throw new InvalidExpressionException("Expression must contain " + FILE_PATH_MACROS + " macros.");
    }

    final TreeMap<Integer,String> map = new TreeMap<>();

    map.put(new Integer(filePathIndex), FILE_STR);

    expression = StringUtil.replace(expression, FILE_PATH_MACROS, FILE_PATH_REGEXP);

    if (lineIndex != -1) {
      expression = StringUtil.replace(expression, LINE_MACROS, NUMBER_REGEXP);
      map.put(new Integer(lineIndex), LINE_STR);
    }

    if (columnIndex != -1) {
      expression = StringUtil.replace(expression, COLUMN_MACROS, NUMBER_REGEXP);
      map.put(new Integer(columnIndex), COLUMN_STR);
    }

    // The block below determines the registers based on the sorted map.
    int count = 0;
    for (final Integer integer : map.keySet()) {
      count++;
      final String s = map.get(integer);

      if (FILE_STR.equals(s)) {
        filePathIndex = count;
      }
      else if (LINE_STR.equals(s)) {
        lineIndex = count;
      }
      else if (COLUMN_STR.equals(s)) {
        columnIndex = count;
      }
    }

    myFileRegister = filePathIndex;
    myLineRegister = lineIndex;
    myColumnRegister = columnIndex;
    myPattern = Pattern.compile(expression, Pattern.MULTILINE);
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public static void validate(String expression) {
    if (StringUtil.isEmptyOrSpaces(expression)) throw new InvalidExpressionException("expression == null or empty");

    expression = substituteMacrosWithRegexps(expression);
    Pattern.compile(expression, Pattern.MULTILINE);
  }

  public Pattern getPattern() {
    return myPattern;
  }

  private static String substituteMacrosWithRegexps(String expression) {
    int filePathIndex = expression.indexOf(FILE_PATH_MACROS);
    int lineIndex = expression.indexOf(LINE_MACROS);
    int columnIndex = expression.indexOf(COLUMN_MACROS);

    if (filePathIndex == -1) {
      throw new InvalidExpressionException("Expression must contain " + FILE_PATH_MACROS + " macros.");
    }

    expression = StringUtil.replace(expression, FILE_PATH_MACROS, FILE_PATH_REGEXP);

    if (lineIndex != -1) {
      expression = StringUtil.replace(expression, LINE_MACROS, NUMBER_REGEXP);
    }

    if (columnIndex != -1) {
      expression = StringUtil.replace(expression, COLUMN_MACROS, NUMBER_REGEXP);
    }
    return expression;
  }

  @Override
  public Result applyFilter(String line, int entireLength) {
    Matcher matcher = myPattern.matcher(StringUtil.newBombedCharSequence(line, 100));
    if (!matcher.find()) {
      return null;
    }

    String filePath = matcher.group(myFileRegister);
    if (filePath == null) {
      return null;
    }

    String lineNumber = "0";
    if (myLineRegister != -1) {
      lineNumber = matcher.group(myLineRegister);
    }

    String columnNumber = "0";
    if (myColumnRegister != -1) {
      columnNumber = matcher.group(myColumnRegister);
    }

    int line1 = 0;
    int column = 0;
    try {
      line1 = Integer.parseInt(lineNumber);
      column = Integer.parseInt(columnNumber);
    } catch (NumberFormatException e) {
      // Do nothing, so that line and column will remain at their initial zero values.
    }

    if (line1 > 0) line1 -= 1;
    if (column > 0) column -= 1;
    // Calculate the offsets relative to the entire text.
    final int highlightStartOffset = entireLength - line.length() + matcher.start(myFileRegister);
    final int highlightEndOffset = highlightStartOffset + filePath.length();
    final HyperlinkInfo info = createOpenFileHyperlink(filePath, line1, column);
    return new Result(highlightStartOffset, highlightEndOffset, info);
  }

  @Nullable
  protected HyperlinkInfo createOpenFileHyperlink(String fileName, final int line, final int column) {
    fileName = fileName.replace(File.separatorChar, '/');
    VirtualFile file = LocalFileSystem.getInstance().findFileByPathIfCached(fileName);
    return file != null ? new OpenFileHyperlinkInfo(myProject, file, line, column) : null;
  }

  public static String[] getMacrosName() {
    return new String[] {FILE_PATH_MACROS, LINE_MACROS, COLUMN_MACROS};
  }
}

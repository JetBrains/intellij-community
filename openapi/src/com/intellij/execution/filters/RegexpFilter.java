/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.filters;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Yura Cangea
 * @version 1.0
 */
public class RegexpFilter implements Filter {
  public static final String FILE_PATH_MACROS = "$FILE_PATH$";
  public static final String LINE_MACROS = "$LINE$";
  public static final String COLUMN_MACROS = "$COLUMN$";

  private static final String FILE_PATH_REGEXP = "((?:\\p{Alpha}\\:)?[0-9 a-z_A-Z\\-\\\\./]+)";
  private static final String NUMBER_REGEXP = "([0-9]+)";

  private int myFileRegister;
  private int myLineRegister;
  private int myColumnRegister;

  private Pattern myPattern;
  private Project myProject;

  public RegexpFilter(Project project, String expression) {
    myProject = project;
    validate(expression);

    if (expression == null || "".equals(expression.trim())) {
      throw new InvalidExpressionException("expression == null or empty");
    }

    int filePathIndex = expression.indexOf(FILE_PATH_MACROS);
    int lineIndex = expression.indexOf(LINE_MACROS);
    int columnIndex = expression.indexOf(COLUMN_MACROS);

    if (filePathIndex == -1) {
      throw new InvalidExpressionException("Expression must contain " + FILE_PATH_MACROS + " macros.");
    }

    final TreeMap<Integer,String> map = new TreeMap<Integer, String>();

    map.put(new Integer(filePathIndex), "file");

    expression = StringUtil.replace(expression, FILE_PATH_MACROS, FILE_PATH_REGEXP);

    if (lineIndex != -1) {
      expression = StringUtil.replace(expression, LINE_MACROS, NUMBER_REGEXP);
      map.put(new Integer(lineIndex), "line");
    }

    if (columnIndex != -1) {
      expression = StringUtil.replace(expression, COLUMN_MACROS, NUMBER_REGEXP);
      map.put(new Integer(columnIndex), "column");
    }

    // The block below determines the registers based on the sorted map.
    int count = 0;
    final Iterator<Integer> itr = map.keySet().iterator();
    while (itr.hasNext()) {
      count++;
      final String s = map.get(itr.next());

      if ("file".equals(s)) {
        filePathIndex = count;
      } else if ("line".equals(s)) {
        lineIndex = count;
      } else if ("column".equals(s)) {
        columnIndex = count;
      }
    }

    myFileRegister = filePathIndex;
    myLineRegister = lineIndex;
    myColumnRegister = columnIndex;
    myPattern = Pattern.compile(expression, Pattern.MULTILINE);
  }

  public static void validate(String expression) {
    if (expression == null || "".equals(expression.trim())) {
      throw new InvalidExpressionException("expression == null or empty");
    }

    expression = substituteMacrosesWithRegexps(expression);

    Pattern.compile(expression, Pattern.MULTILINE);
  }

  private static String substituteMacrosesWithRegexps(String expression) {
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

  public Result applyFilter(final String line, final int entireLength) {

    final Matcher matcher = myPattern.matcher(line);
    if (matcher.find()) {
      return createResult(matcher, entireLength - line.length());
    }

    return null;
  }

  private Result createResult(final Matcher matcher, final int entireLen) {
    final String filePath = matcher.group(myFileRegister);

    String lineNumber = "0";
    String columnNumber = "0";

    if (myLineRegister != -1) {
      lineNumber = matcher.group(myLineRegister);
    }

    if (myColumnRegister != -1) {
      columnNumber = matcher.group(myColumnRegister);
    }

    int line = 0;
    int column = 0;
    try {
      line = Integer.parseInt(lineNumber);
      column = Integer.parseInt(columnNumber);
    } catch (NumberFormatException e) {
      // Do nothing, so that line and column will remain at their initial
      // zero values.
    }

    if (line > 0) line -= 1;
    if (column > 0) column -= 1;
    // Calculate the offsets relative to the entire text.
    final int highlightStartOffset = entireLen + matcher.start(myFileRegister);
    final int highlightEndOffset = highlightStartOffset + filePath.length();

    final HyperlinkInfo info = createOpenFileHyperlink(filePath, line, column);
    return new Result(highlightStartOffset, highlightEndOffset, info);
  }

  protected HyperlinkInfo createOpenFileHyperlink(String fileName, final int line, final int column) {
    fileName = fileName.replace(File.separatorChar, '/');
    final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(fileName);
    if (file == null) return null;
    return new OpenFileHyperlinkInfo(myProject, file, line, column);
  }

  public static String[] getMacrosName() {
    return new String[] {FILE_PATH_MACROS, LINE_MACROS, COLUMN_MACROS};
  }
}

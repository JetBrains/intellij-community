// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.unscramble;

import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.threadDumpParser.ThreadDumpParser;
import com.intellij.threadDumpParser.ThreadState;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.regex.Pattern;

final public class UnscrambleUtils {
  private static final Condition<ThreadState> DEADLOCK_CONDITION = state -> state.isDeadlocked();
  private static final Pattern STACKTRACE_LINE =
    Pattern.compile(
      "[\t]*at [[_a-zA-Z0-9/]+\\.]+[_a-zA-Z$0-9/]+\\.[a-zA-Z0-9_/]+\\([A-Za-z0-9_/]+\\.(java|kt):[\\d]+\\)+[ [~]*\\[[a-zA-Z0-9\\.\\:/]\\]]*");

  public static @Nullable String getExceptionName(String unscrambledTrace) {
    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
    BufferedReader reader = new BufferedReader(new StringReader(unscrambledTrace));
    for (int i = 0; i < 3; i++) {
      try {
        String line = reader.readLine();
        if (line == null) return null;
        String name = getExceptionAbbreviation(line);
        if (name != null) return name;
      }
      catch (IOException e) {
        return null;
      }
    }
    return null;
  }

  private static @Nullable String getExceptionAbbreviation(String line) {
    line = StringUtil.trimStart(line.trim(), "Caused by: ");
    int classNameStart = 0;
    int classNameEnd = line.length();
    for (int j = 0; j < line.length(); j++) {
      char c = line.charAt(j);
      if (c == '.' || c == '$') {
        classNameStart = j + 1;
        continue;
      }
      if (c == ':') {
        classNameEnd = j;
        break;
      }
      if (!StringUtil.isJavaIdentifierPart(c)) {
        return null;
      }
    }
    if (classNameStart >= classNameEnd) return null;
    String clazz = line.substring(classNameStart, classNameEnd);
    String abbreviate = abbreviate(clazz);
    return abbreviate.length() > 1 ? abbreviate : clazz;
  }

  private static String abbreviate(String s) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (Character.isUpperCase(c)) {
        builder.append(c);
      }
    }
    return builder.toString();
  }

  public static RunContentDescriptor addConsole(final Project project,
                                                final List<ThreadState> threadDump,
                                                String unscrambledTrace,
                                                Boolean withExecutor) {
    Icon icon = null;
    String message = JavaBundle.message("unscramble.unscrambled.stacktrace.tab");
    if (!threadDump.isEmpty()) {
      message = JavaBundle.message("unscramble.unscrambled.threaddump.tab");
      icon = AllIcons.Actions.Dump;
    }
    else {
      String name = getExceptionName(unscrambledTrace);
      if (name != null) {
        message = name;
        icon = AllIcons.Actions.Lightning;
      }
    }
    if (ContainerUtil.find(threadDump, DEADLOCK_CONDITION) != null) {
      message = JavaBundle.message("unscramble.unscrambled.deadlock.tab");
      icon = AllIcons.Debugger.KillProcess;
    }
    return AnalyzeStacktraceUtil.addConsole(project, threadDump.size() > 1 ? new ThreadDumpConsoleFactory(project, threadDump) : null,
                                            message, unscrambledTrace, icon, withExecutor);
  }

  public static RunContentDescriptor addConsole(final Project project, final List<ThreadState> threadDump, String unscrambledTrace) {
    return addConsole(project, threadDump, unscrambledTrace, true);
  }

  public static boolean isStackTrace(String text) {
    text = ThreadDumpParser.normalizeText(text);
    int linesCount = 0;
    for (String line : text.split("\n")) {
      line = line.trim();
      if (line.length() == 0) continue;
      line = StringUtil.trimEnd(line, "\r");
      if (STACKTRACE_LINE.matcher(line).matches()) {
        linesCount++;
      }
      else {
        linesCount = 0;
      }
      if (linesCount > 2) return true;
    }
    return false;
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.rt.execution.testFrameworks;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Clone of GeneralCommandLine.
 */
public class ProcessBuilder {
  public static final boolean isWindows = isWindows();

  private static boolean isWindows() {
    try {
      return System.getProperty("os.name").toLowerCase(Locale.ENGLISH).startsWith("windows");
    }
    catch (SecurityException e) {
      return false;
    }
  }

  private static final String WIN_SHELL_SPECIALS = "&<>()@^|";

  protected final List<String> myParameters = new ArrayList<>();
  private File myWorkingDir = null;

  public void add(final String parameter) {
    myParameters.add(parameter);
  }

  public void add(final List<String> parameters) {
    for (String parameter : parameters) {
      add(parameter);
    }
  }

  public void setWorkingDir(File workingDir) {
    myWorkingDir = workingDir;
  }

  // please keep an implementation in sync with [util] CommandLineUtil.toCommandLine()
  //
  // Comparing to the latter, this is a simplified version with the following limitations on Windows (neither of these
  // seems to be used in our cases though):
  //
  //   - does not fully handle \" escaping (must escape quoted ["C:\Program Files\"] -> [\"C:\Program Files\\\"])
  //   - does not support `cmd.exe /c call command args-with-special-chars-[&<>()@^|]
  //   - does not handle special chars [&<>()@^|] interleaved with quotes ["] properly (the quote flag)
  //   - mangles the output of `cmd.exe /c echo ...`
  //
  // If either of these becomes an issue, please refer to [util] CommandLineUtil.addToWindowsCommandLine() for a possible implementation.
  public Process createProcess() throws IOException {
    if (myParameters.isEmpty()) {
      throw new IllegalArgumentException("Executable name not specified");
    }

    String command = myParameters.get(0);
    boolean winShell = isWindows && isWinShell(command);

    String[] commandLine = new String[myParameters.size()];
    commandLine[0] = command;

    for (int i = 1; i < myParameters.size(); i++) {
      String parameter = myParameters.get(i);

      if (isWindows) {
        int pos = parameter.indexOf('\"');
        if (pos >= 0) {
          StringBuilder buffer = new StringBuilder(parameter);
          do {
            buffer.insert(pos, '\\');
            pos += 2;
          }
          while ((pos = parameter.indexOf('\"', pos)) >= 0);
          parameter = buffer.toString();
        }
        else if (parameter.isEmpty()) {
          parameter = "\"\"";
        }

        if (winShell && containsAnyChar(parameter, WIN_SHELL_SPECIALS)) {
          parameter = '"' + parameter + '"';
        }
      }

      commandLine[i] = parameter;
    }

    return Runtime.getRuntime().exec(commandLine, null, myWorkingDir);
  }

  private static boolean isWinShell(String command) {
    return endsWithIgnoreCase(command, ".cmd") || endsWithIgnoreCase(command, ".bat") ||
           "cmd".equalsIgnoreCase(command) || "cmd.exe".equalsIgnoreCase(command);
  }

  private static boolean endsWithIgnoreCase(String str, String suffix) {
    return str.regionMatches(true, str.length() - suffix.length(), suffix, 0, suffix.length());
  }

  private static boolean containsAnyChar(String value, String chars) {
    for (int i = 0; i < value.length(); i++) {
      if (chars.indexOf(value.charAt(i)) >= 0) {
        return true;
      }
    }
    return false;
  }
}
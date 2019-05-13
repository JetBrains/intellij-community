/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
      return System.getProperty("os.name").toLowerCase(Locale.US).startsWith("windows");
    }
    catch (SecurityException e) {
      return false;
    }
  }

  private static final String WIN_SHELL_SPECIALS = "&<>()@^|";

  private final List myParameters = new ArrayList();
  private File myWorkingDir = null;

  public void add(final String parameter) {
    myParameters.add(parameter);
  }

  public void add(final List parameters) {
    for (int i = 0; i < parameters.size(); i++) {
      add((String)parameters.get(i));
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
    if (myParameters.size() < 1) {
      throw new IllegalArgumentException("Executable name not specified");
    }

    String command = myParameters.get(0).toString();
    boolean winShell = isWindows && isWinShell(command);

    String[] commandLine = new String[myParameters.size()];
    commandLine[0] = command;

    for (int i = 1; i < myParameters.size(); i++) {
      String parameter = myParameters.get(i).toString();

      if (isWindows) {
        int pos = parameter.indexOf('\"');
        if (pos >= 0) {
          StringBuffer buffer = new StringBuffer(parameter);
          do {
            buffer.insert(pos, '\\');
            pos += 2;
          }
          while ((pos = parameter.indexOf('\"', pos)) >= 0);
          parameter = buffer.toString();
        }
        else if (parameter.length() == 0) {
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
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ether;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 */
public final class LogParser {

  private static final String COMPILING_START_STR = "incremental.java.JavaBuilder - Compiling";

  public static void main(String[] args) throws IOException {
    final String logPath = args[0];

    long totalTime = 0L;
    int totalFileCount = 0;
    try (BufferedReader reader = new BufferedReader(new FileReader(logPath))) {
      String line = reader.readLine();
      while (line != null) {
        if (line.contains(COMPILING_START_STR)) {
          final long startTime = getTime(line);
          final String nextLine = reader.readLine();
          if (nextLine != null && nextLine.contains("- Dependency analysis found")) {
            final long endTime = getTime(nextLine);
            totalTime += (endTime - startTime);
            final int index = line.indexOf(COMPILING_START_STR);
            if (index > 0) {
              final StringBuilder buf = new StringBuilder();
              for (int idx = index + COMPILING_START_STR.length(); idx < line.length(); idx++) {
                final char ch = line.charAt(idx);
                if (ch == ' ' || ch == '\t') {
                  continue;
                }
                if (!Character.isDigit(ch)) {
                  break;
                }
                buf.append(ch);
              }
              if (buf.length() > 0) {
                final int fileCount = Integer.parseInt(buf.toString());
                totalFileCount += fileCount;
              }
            }
          }
        }
        line = reader.readLine();
      }
    }

    long millis = totalTime % 1000;
    long seconds = totalTime / 1000;
    long minutes = seconds / 60;
    seconds = seconds % 60;

    System.out.println("Files compiled: " + totalFileCount);
    System.out.println("Total time spent compiling java " + minutes + " min " + seconds + " sec " + millis + " ms");
  }


  private static final int HOURS_START = 11;
  private static final int MINUTES_START = HOURS_START + 3;
  private static final int SECONDS_START = MINUTES_START + 3;
  private static final int MILLIS_START = SECONDS_START + 3;

  private static long getTime(String line) {
    final int hours = Integer.parseInt(line.substring(HOURS_START, HOURS_START + 2));
    final int minutes = Integer.parseInt(line.substring(MINUTES_START, MINUTES_START + 2));
    final int seconds = Integer.parseInt(line.substring(SECONDS_START, SECONDS_START + 2));
    final int millis = Integer.parseInt(line.substring(MILLIS_START, MILLIS_START + 3));
    return millis + seconds * 1000L + minutes * 60000L + hours * 3600000L;
  }

}

/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.internal.statistic.analytics;

import com.intellij.openapi.util.io.FileUtil;
import java.util.Scanner;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

public class StudioCrashDetails {

  /**
   * Represents a crash for which there is no additional details. Assumed to be not a JVM crash.
   */
  public final static StudioCrashDetails UNKNOWN = new StudioCrashDetails("<unknown>", false, -1, "", "", "", "");
  private final static String JVM_CRASH_FILE_STRING_FORMAT =
    System.getProperty("user.home") + File.separator + "java_error_in_STUDIO_%d.log";

  private final String myDescription;
  private final boolean myJvmCrash;
  private final long myUptimeInMs;
  private final String myErrorSignal;
  private final String myErrorFrame;
  private final String myErrorThread;
  private final String myNativeStack;

  private StudioCrashDetails(
    String description,
    boolean isJvmCrash,
    long uptimeInMs,
    String errorSignal,
    String errorFrame,
    String errorThread,
    String nativeStack
  ) {
    myDescription = description;
    myJvmCrash = isJvmCrash;
    myUptimeInMs = uptimeInMs;
    myErrorSignal = errorSignal;
    myErrorFrame = errorFrame;
    myErrorThread = errorThread;
    myNativeStack = nativeStack;
  }

  @NotNull
  public static StudioCrashDetails loadFromRecordFile(File record) throws IOException {
    final List<String> lines = FileUtil.loadLines(record);
    String buildNumber = !lines.isEmpty() ? lines.get(0) : "";
    String runtimeVersion = lines.size() > 1 ? lines.get(1) : "";
    long startupDateInMs = -1;
    if (lines.size() > 2) {
      try {
        startupDateInMs = Long.parseLong(lines.get(2));
      }
      catch (NumberFormatException ignore) {
      }
    }
    long pid = -1;
    if (lines.size() > 3) {
      try {
        pid = Long.parseLong(lines.get(3));
      }
      catch (NumberFormatException ignore) {
      }
    }
    boolean isJvmCrash = false;
    long uptimeInMs = -1;
    String errorSignal = "";
    String errorFrame = "";
    String errorThread = "";
    String nativeStack = "";
    // Assume it was not a JVM crash if there is no startup time or pid
    if (startupDateInMs != -1 && pid >= 0) {
      // Check time of creation of the crash report file. If it happened after the app startup time then
      // it is likely a crash report from that run.
      Path jvmCrashReportFile = Paths.get(String.format(JVM_CRASH_FILE_STRING_FORMAT, pid));
      if (Files.exists(jvmCrashReportFile)) {
        BasicFileAttributes attrs = Files.readAttributes(jvmCrashReportFile, BasicFileAttributes.class);
        long crashDateInMs = attrs.creationTime().toMillis();
        if (crashDateInMs > startupDateInMs) {
          isJvmCrash = true;
          uptimeInMs = crashDateInMs - startupDateInMs;
          try (Scanner scanner = new Scanner(jvmCrashReportFile)) {
            while (scanner.hasNext() && (errorSignal.isEmpty() || errorFrame.isEmpty() || errorThread.isEmpty() || nativeStack.isEmpty())) {
              if (scanner.findInLine("#  SIG") != null) {
                errorSignal = "SIG" + scanner.nextLine();
              } else if (scanner.findInLine("#  EXCEPTION") != null) {
                errorSignal = "EXCEPTION" + scanner.nextLine();
              } else if (scanner.findInLine("# Problematic frame:") != null) {
                scanner.nextLine();
                errorFrame = scanner.nextLine().substring(2);
              } else if (scanner.findInLine("Current thread \\(.+\\):") != null) {
                errorThread = scanner.nextLine().trim();
              } else if (scanner.findInLine("Native frames:") != null) {
                scanner.nextLine();
                StringBuilder nativeStackBuilder = new StringBuilder();
                while (scanner.hasNext()) {
                  String line = scanner.nextLine();
                  if (line.isEmpty()) {
                    nativeStack = nativeStackBuilder.toString();
                    break;
                  }
                  nativeStackBuilder.append(line.trim()).append('\n');
                }
              } else {
                scanner.nextLine();
              }
            }
          }
        }
      }
    }
    String description = buildNumber + "\n" + runtimeVersion;
    return new StudioCrashDetails(description, isJvmCrash, uptimeInMs, errorSignal, errorFrame, errorThread, nativeStack);
  }

  public boolean isJvmCrash() {
    return myJvmCrash;
  }

  public String getDescription() {
    return myDescription;
  }

  public long getUptimeInMs() {
    return myUptimeInMs;
  }

  public String getErrorSignal() {
    return myErrorSignal;
  }

  public String getErrorFrame() {
    return myErrorFrame;
  }

  public String getErrorThread() {
    return myErrorThread;
  }

  public String getNativeStack() {
    return myNativeStack;
  }
}

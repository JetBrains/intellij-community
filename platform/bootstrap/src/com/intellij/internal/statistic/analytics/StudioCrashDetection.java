/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.SystemInfo;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileFilter;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Static utility methods to detect when Android Studio has crashed.
 * <p>
 *   File format of the record file:<br>
 *   1st line: Android Studio build version number<br>
 *   2nd line: JVM runtime version<br>
 *   3rd line: JVM start time (milliseconds since 1970) (optional)<br>
 *   4th line: PID of Android Studio (optional)<br>
 * </p>
 */
public class StudioCrashDetection {
  private static final String RECORD_FILE_KEY = "studio.record.file";
  private static final String PLATFORM_PREFIX = "AndroidStudio";
  private static final String LINE_SEPARATOR = System.getProperty("line.separator");
  private static ArrayList<StudioCrashDetails> ourCrashes;

  private StudioCrashDetection() {
  }

  /**
   * Creates a record of the application starting, unique to this run.
   *
   * @throws AssertionError if called more than once per run
   */
  public static void start() {
    if (System.getProperty(RECORD_FILE_KEY) != null) throw new AssertionError("StudioCrashDetection.start called more than once");
    try {
      File f = new File(PathManager.getTempPath(),
                        String.format("%s.%s", PLATFORM_PREFIX, UUID.randomUUID().toString()));
      if (f.createNewFile()) {
        // We use a system property to pass the filename across classloaders.
        System.setProperty(RECORD_FILE_KEY, f.getAbsolutePath());

        try (FileWriter fw = new FileWriter(f)) {
          File buildInfo = new File(PathManager.getHomePath(), "build.txt");
          if (!buildInfo.exists() && SystemInfo.isMac) {
            // On a Mac, also try to find it under Resources.
            buildInfo = new File(PathManager.getHomePath(), "Resources/build.txt");
          }

          String buildVersion = "<unknown>";
          if (buildInfo.exists()) {
            List<String> lines = Files.readAllLines(buildInfo.toPath());
            if (!lines.isEmpty()) {
              buildVersion = lines.get(0);
            }
          }
          fw.write(buildVersion);
          fw.write(LINE_SEPARATOR);
          fw.write(System.getProperty("java.runtime.version"));
          fw.write(LINE_SEPARATOR);
          fw.write(String.valueOf(ManagementFactory.getRuntimeMXBean().getStartTime()));
          fw.write(LINE_SEPARATOR);
          fw.write(String.valueOf(getMyPID()));
        }
      }
    }
    catch (IOException ex) {
      // continue anyway.
    }
  }

  private static long getMyPID() {
    String pidAndMachineName = ManagementFactory.getRuntimeMXBean().getName();
    String[] split = pidAndMachineName.split("@");
    long pid = -1;
    if (split.length == 2) {
      try {
        pid = Long.parseLong(split[0]);
      }
      catch (NumberFormatException ignore) {
      }
    }
    return pid;
  }

  /**
   * Updates the record created by {@link #start} in this run with the accurate version number.
   */
  public static void updateRecordedVersionNumber(@NotNull String version) {
    String recordFileName = System.getProperty(RECORD_FILE_KEY);

    if (recordFileName != null) {
      File recordFile = new File(recordFileName);
      try {
        List<String> lines = Files.readAllLines(recordFile.toPath());
        lines.set(0, version);

        try (FileWriter fw = new FileWriter(recordFile)) {
          for (String line : lines) {
            fw.write(line);
            fw.write(LINE_SEPARATOR);
          }
        }
      }
      catch (IOException ex) {
        // continue anyway.
      }
    }
  }

  /**
   * Deletes the record created by {@link #start} for this run, if it exists.
   */
  public static void stop() {
    String recordFileName = System.getProperty(RECORD_FILE_KEY);
    if (recordFileName != null) {
      try {
        Files.deleteIfExists(Paths.get(recordFileName));
      } catch (IOException ignored) {
        // Ignore.
      }
      System.clearProperty(RECORD_FILE_KEY);
    }
  }

  /**
   * Returns and deletes any records created by {@link #start} in previous runs.
   */
  public static List<StudioCrashDetails> reapCrashDescriptions() {
    if (ourCrashes != null) {
      return ourCrashes;
    }
    File[] previousRecords = new File(PathManager.getTempPath()).listFiles(
      new FileFilter() {
        final String recordFile = System.getProperty(RECORD_FILE_KEY);

        @Override
        public boolean accept(File pathname) {
          return pathname.getName().startsWith(PLATFORM_PREFIX) && !pathname.getAbsolutePath().equals(recordFile);
        }
      });
    ourCrashes = new ArrayList<>();
    if (previousRecords != null) {
      for (File record : previousRecords) {
        StudioCrashDetails crash;
        try {
          crash = StudioCrashDetails.loadFromRecordFile(record);
        }
        catch (IOException ignored) {
          crash = StudioCrashDetails.UNKNOWN;
        }
        try {
          if (Files.deleteIfExists(record.toPath())) {
            ourCrashes.add(crash);
          }
        } catch (IOException ignored) {
          // Ignore
        }
      }
    }
    return ourCrashes;
  }
}

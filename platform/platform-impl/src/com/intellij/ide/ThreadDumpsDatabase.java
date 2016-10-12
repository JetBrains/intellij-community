/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.intellij.ide;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Collectors;

public class ThreadDumpsDatabase {
  private final Object myDbLock = new Object();
  private final Path myDb;

  public ThreadDumpsDatabase(@NotNull File databaseFile) {
    myDb = databaseFile.toPath();
  }

  public void appendThreadDump(@NotNull Path threadDumpPath) throws IOException {
    String content = threadDumpPath.toString() + "\n";
    synchronized (myDbLock) {
      Files.write(myDb, content.getBytes(Charsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
  }

  @NotNull
  public List<Path> reapThreadDumps() {
    List<String> lines;

    synchronized (myDbLock) {
      try {
        lines = Files.readAllLines(myDb);
        Files.write(myDb, new byte[0], StandardOpenOption.TRUNCATE_EXISTING);
      }
      catch (IOException e) {
        return ImmutableList.of();
      }
    }

    return lines.stream()
      .map(String::trim)
      .filter(line -> !line.isEmpty())
      .map(Paths::get)
      .filter(Files::isReadable)
      .collect(Collectors.toList());
  }
}

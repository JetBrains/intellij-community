/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.builders.impl.logging;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.jps.builders.logging.ProjectBuilderLogger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author nik
 */
public abstract class ProjectBuilderLoggerBase implements ProjectBuilderLogger {
  @Override
  public void logDeletedFiles(Collection<String> paths) {
    if (paths.isEmpty()) return;
    final String[] buffer = new String[paths.size()];
    int i = 0;
    for (final String o : paths) {
      buffer[i++] = o;
    }
    Arrays.sort(buffer);
    logLine("Cleaning output files:");
    for (final String o : buffer) {
      logLine(o);
    }
    logLine("End of files");
  }

  @Override
  public void logCompiledFiles(Collection<File> files, String builderName, final String description) throws IOException {
    logLine(description);
    final String[] buffer = new String[files.size()];
    int i = 0;
    for (final File f : files) {
      buffer[i++] = FileUtil.toSystemIndependentName(f.getCanonicalPath());
    }
    Arrays.sort(buffer);
    for (final String s : buffer) {
      logLine(s);
    }
    logLine("End of files");
  }

  @Override
  public void logCompiledPaths(Collection<String> paths, String builderName, String description) throws IOException {
    List<File> files = new ArrayList<File>(paths.size());
    for (String path : paths) {
      files.add(new File(path));
    }
    logCompiledFiles(files, builderName, description);
  }

  protected abstract void logLine(String message);
}

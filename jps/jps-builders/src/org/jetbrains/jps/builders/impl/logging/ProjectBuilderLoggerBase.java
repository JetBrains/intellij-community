// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.impl.logging;

import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.logging.ProjectBuilderLogger;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Function;

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
  public void logCompiledFiles(Collection<File> files, String builderId, final String description) throws IOException {
    logCompiled(files, description, file -> {
      try {
        return FileUtilRt.toSystemIndependentName(file.getCanonicalPath());
      }
      catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    });
  }

  private <T> void logCompiled(@NotNull Collection<T> files, String description, @NotNull Function<T, String> toString) {
    logLine(description);
    String[] buffer = new String[files.size()];
    int i = 0;
    for (T file : files) {
      buffer[i++] = toString.apply(file);
    }
    Arrays.sort(buffer);
    for (final String s : buffer) {
      logLine(s);
    }
    logLine("End of files");
  }

  @Override
  public void logCompiled(@NotNull Collection<Path> files, String builderId, final String description) {
    logCompiled(files, description, file -> {
      return FileUtilRt.toSystemIndependentName(file.toAbsolutePath().normalize().toString());
    });
  }

  @Override
  public void logCompiledPaths(@NotNull Collection<String> paths, String builderId, String description) {
    logCompiled(paths, description, file -> {
      return FileUtilRt.toSystemIndependentName(Path.of(file).toAbsolutePath().normalize().toString());
    });
  }

  protected abstract void logLine(@NonNls String message);
}

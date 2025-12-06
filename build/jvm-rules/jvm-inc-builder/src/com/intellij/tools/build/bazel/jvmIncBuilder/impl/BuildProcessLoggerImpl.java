package com.intellij.tools.build.bazel.jvmIncBuilder.impl;

import com.intellij.tools.build.bazel.jvmIncBuilder.BuildProcessLogger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.jetbrains.jps.util.Iterators.*;

public class BuildProcessLoggerImpl implements BuildProcessLogger {
  private final StringBuilder myBuf = new StringBuilder();
  private final Path myBaseDir;

  public BuildProcessLoggerImpl(Path baseDir) {
    myBaseDir = baseDir;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  public String getCollectedData() {
    try {
      return myBuf.toString();
    }
    finally {
      myBuf.setLength(0);
      myBuf.trimToSize();
    }
  }

  @Override
  public void logDeletedPaths(Iterable<String> paths) {
    if (isEmpty(paths)) {
      return;
    }
    List<String> sorted = collect(map(paths, BuildProcessLoggerImpl::formatPath), new ArrayList<>());
    Collections.sort(sorted);
    logLine("Cleaning output files:");
    for (final String line : sorted) {
      logLine(line);
    }
    logLine("End of files");
  }

  @Override
  public void logCompiledPaths(Iterable<Path> files, String builderId, String description) {
    logLine(description);
    List<String> sorted = collect(map(files, path -> formatPath(myBaseDir.relativize(path).toString())), new ArrayList<>());
    Collections.sort(sorted);
    for (final String s : sorted) {
      logLine(s);
    }
    logLine("End of files");
  }

  private void logLine(String line) {
    myBuf.append(line).append("\n");
  }

  private static @NotNull String formatPath(String path) {
    return "  " + path.replace(File.separatorChar, '/');
  }
}

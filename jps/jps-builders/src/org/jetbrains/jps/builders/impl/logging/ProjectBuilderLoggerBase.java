package org.jetbrains.jps.builders.impl.logging;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.jps.builders.logging.ProjectBuilderLogger;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

/**
 * @author nik
 */
public abstract class ProjectBuilderLoggerBase implements ProjectBuilderLogger {
  @Override
  public void logDeletedFiles(Collection<String> outputs) {
    if (outputs.isEmpty()) return;
    final String[] buffer = new String[outputs.size()];
    int i = 0;
    for (final String o : outputs) {
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

  protected abstract void logLine(String message);
}

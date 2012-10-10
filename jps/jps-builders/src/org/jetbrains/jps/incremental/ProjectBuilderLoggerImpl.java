package org.jetbrains.jps.incremental;

import com.intellij.openapi.diagnostic.Logger;

import java.util.Arrays;
import java.util.Collection;

/**
 * @author nik
 */
public class ProjectBuilderLoggerImpl implements ProjectBuilderLogger {
  private static final Logger LOG = Logger.getInstance(ProjectBuilderLoggerImpl.class);

  @Override
  public boolean isEnabled() {
    return LOG.isDebugEnabled();
  }

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

  protected void logLine(final String message) {
    LOG.debug(message);
  }
}

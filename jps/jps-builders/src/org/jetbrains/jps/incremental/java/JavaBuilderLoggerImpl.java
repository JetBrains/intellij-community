package org.jetbrains.jps.incremental.java;

import com.intellij.openapi.diagnostic.Logger;

/**
 * @author nik
 */
public class JavaBuilderLoggerImpl implements JavaBuilderLogger {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.java.JavaBuilderLoggerImpl");

  @Override
  public void log(String line) {
    LOG.debug(line);
  }

  @Override
  public boolean isEnabled() {
    return LOG.isDebugEnabled();
  }
}

package org.jetbrains.jps.builders.impl.logging;

import com.intellij.openapi.diagnostic.Logger;

/**
 * @author nik
 */
public class ProjectBuilderLoggerImpl extends ProjectBuilderLoggerBase {
  private static final Logger LOG = Logger.getInstance(ProjectBuilderLoggerImpl.class);

  @Override
  public boolean isEnabled() {
    return LOG.isDebugEnabled();
  }

  @Override
  protected void logLine(final String message) {
    LOG.debug(message);
  }
}

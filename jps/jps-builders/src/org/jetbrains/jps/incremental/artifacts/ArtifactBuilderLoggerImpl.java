package org.jetbrains.jps.incremental.artifacts;

import com.intellij.openapi.diagnostic.Logger;

/**
 * @author nik
 */
public class ArtifactBuilderLoggerImpl implements ArtifactBuilderLogger {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.artifacts.ArtifactBuilderLoggerImpl");

  @Override
  public void fileCopied(String sourceFilePath) {
    LOG.debug("Copied:" + sourceFilePath);
  }

  @Override
  public void fileDeleted(String targetFilePath) {
    LOG.debug("Deleted:" + targetFilePath);
  }

  @Override
  public boolean isEnabled() {
    return LOG.isDebugEnabled();
  }
}

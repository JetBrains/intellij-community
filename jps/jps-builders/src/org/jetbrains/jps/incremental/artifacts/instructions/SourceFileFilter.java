package org.jetbrains.jps.incremental.artifacts.instructions;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class SourceFileFilter {
  public static final SourceFileFilter ALL = new SourceFileFilter() {
    @Override
    public boolean accept(@NotNull String fullFilePath) {
      return true;
    }
  };

  public abstract boolean accept(@NotNull String fullFilePath);
}

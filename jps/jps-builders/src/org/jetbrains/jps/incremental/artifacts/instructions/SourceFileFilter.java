package org.jetbrains.jps.incremental.artifacts.instructions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.storage.BuildDataManager;

import java.io.IOException;

/**
 * @author nik
 */
public abstract class SourceFileFilter {
  public static final SourceFileFilter ALL = new SourceFileFilter() {
    @Override
    public boolean accept(@NotNull String fullFilePath, BuildDataManager dataManager) {
      return true;
    }
  };

  public abstract boolean accept(@NotNull String fullFilePath, BuildDataManager dataManager) throws IOException;
}

package org.jetbrains.jps.incremental.artifacts.instructions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.cmdline.ProjectDescriptor;

import java.io.IOException;

/**
 * @author nik
 */
public abstract class SourceFileFilter {
  public static final SourceFileFilter ALL = new SourceFileFilter() {
    @Override
    public boolean accept(@NotNull String fullFilePath, ProjectDescriptor projectDescriptor) {
      return true;
    }
  };

  public abstract boolean accept(@NotNull String fullFilePath, ProjectDescriptor projectDescriptor) throws IOException;
}

package org.jetbrains.jps.builders;

import java.io.File;
import java.io.FileFilter;

/**
 * @author nik
 */
public abstract class BuildRootDescriptor {
  public abstract String getRootId();

  public abstract File getRootFile();

  public abstract BuildTarget<?> getTarget();

  public abstract FileFilter createFileFilter();

  public boolean isGenerated() {
    return false;
  }
}

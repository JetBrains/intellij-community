package org.jetbrains.jps.builders;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.cmdline.ProjectDescriptor;

import java.io.File;
import java.io.FileFilter;
import java.util.Collections;
import java.util.Set;

/**
 * @author nik
 */
public abstract class BuildRootDescriptor {
  public abstract String getRootId();

  public abstract File getRootFile();

  public abstract BuildTarget<?> getTarget();

  public abstract FileFilter createFileFilter(@NotNull ProjectDescriptor descriptor);

  /**
   * @return the set of excluded directories under this root
   */
  @NotNull
  public Set<File> getExcludedRoots() {
    return Collections.emptySet();
  }

  public boolean isGenerated() {
    return false;
  }

  public boolean canUseFileCache() {
    return false;
  }
}

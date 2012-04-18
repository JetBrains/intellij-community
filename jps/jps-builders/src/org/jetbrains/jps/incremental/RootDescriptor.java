package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.Module;

import java.io.File;

/**
* @author Eugene Zhuravlev
*         Date: 1/3/12
*/
public final class RootDescriptor {
  @NotNull
  public final Module module;
  @NotNull
  public final File root;
  public final boolean isTestRoot;
  public final boolean isGeneratedSources;

  RootDescriptor(@NotNull Module module, @NotNull File root, boolean isTestRoot) {
    this(module, root, isTestRoot, false);
  }
  RootDescriptor(@NotNull Module module, @NotNull File root, boolean isTestRoot, boolean isGenerated) {
    this.module = module;
    this.root = root;
    this.isTestRoot = isTestRoot;
    this.isGeneratedSources = isGenerated;
  }
}

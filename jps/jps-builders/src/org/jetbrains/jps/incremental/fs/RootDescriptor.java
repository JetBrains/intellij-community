package org.jetbrains.jps.incremental.fs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.ModuleBuildTarget;

import java.io.File;

/**
* @author Eugene Zhuravlev
*         Date: 1/3/12
*/
public final class RootDescriptor {
  @NotNull
  public final String module;
  @NotNull
  public final File root;
  @NotNull
  public final ModuleBuildTarget target;
  public final boolean isTestRoot;
  public final boolean isGeneratedSources;
  public final boolean isTemp;

  public RootDescriptor(@NotNull final String moduleName,
                        @NotNull File root,
                        @NotNull ModuleBuildTarget target,
                        boolean isTestRoot,
                        boolean isGenerated,
                        boolean isTemp) {
    this.module = moduleName;
    this.root = root;
    this.target = target;
    this.isTestRoot = isTestRoot;
    this.isGeneratedSources = isGenerated;
    this.isTemp = isTemp;
  }

  @Override
  public String toString() {
    return "RootDescriptor{" +
           "module='" + module + '\'' +
           ", root=" + root +
           ", test=" + isTestRoot +
           ", generated=" + isGeneratedSources +
           '}';
  }
}

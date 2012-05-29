package org.jetbrains.jps.incremental.fs;

import org.jetbrains.annotations.NotNull;

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
  public final boolean isTestRoot;
  public final boolean isGeneratedSources;

  public RootDescriptor(@NotNull final String moduleName, @NotNull File root, boolean isTestRoot, boolean isGenerated) {
    this.module = moduleName;
    this.root = root;
    this.isTestRoot = isTestRoot;
    this.isGeneratedSources = isGenerated;
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

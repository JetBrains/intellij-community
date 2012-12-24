package org.jetbrains.jps.builders.impl;

import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.cmdline.ProjectDescriptor;

import java.io.File;
import java.io.FileFilter;

public class BuildRootDescriptorImpl extends BuildRootDescriptor {
  private final File myRoot;
  private final BuildTarget myTarget;
  private final boolean myCanUseFileCache;

  public BuildRootDescriptorImpl(BuildTarget target, File root) {
    myTarget = target;
    myRoot = root;
    myCanUseFileCache = super.canUseFileCache();
  }

  public BuildRootDescriptorImpl(BuildTarget target, File root, boolean canUseFileCache) {
    myTarget = target;
    myRoot = root;
    myCanUseFileCache = canUseFileCache;
  }

  @Override
  public String getRootId() {
    return FileUtilRt.toSystemIndependentName(myRoot.getAbsolutePath());
  }

  @Override
  public File getRootFile() {
    return myRoot;
  }

  @Override
  public BuildTarget<?> getTarget() {
    return myTarget;
  }

  @Override
  public FileFilter createFileFilter(@NotNull ProjectDescriptor descriptor) {
    return new FileFilter() {
      @Override
      public boolean accept(@NotNull File pathname) {
        return true;
      }
    };
  }

  @Override
  public boolean canUseFileCache() {
    return myCanUseFileCache;
  }
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.impl;

import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.BuildTarget;

import java.io.File;

@ApiStatus.Internal
public class BuildRootDescriptorImpl extends BuildRootDescriptor {
  private final File myRoot;
  private final BuildTarget<?> myTarget;

  public BuildRootDescriptorImpl(BuildTarget<?> target, File root) {
    this(target, root, false);
  }

  public BuildRootDescriptorImpl(BuildTarget<?> target, File root, boolean ignored) {
    myTarget = target;
    myRoot = root;
  }

  @Override
  public @NotNull String getRootId() {
    return FileUtilRt.toSystemIndependentName(myRoot.getAbsolutePath());
  }

  @Override
  public @NotNull File getRootFile() {
    return myRoot;
  }

  @Override
  public @NotNull BuildTarget<?> getTarget() {
    return myTarget;
  }
}

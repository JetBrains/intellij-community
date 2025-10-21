// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl;

import com.intellij.tools.build.bazel.jvmIncBuilder.NodeSourceSnapshot;
import com.intellij.tools.build.bazel.jvmIncBuilder.ResourceGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.NodeSource;

public final class ResourcesSnapshotDelta extends ElementSnapshotDeltaImpl<NodeSource> {
  private final Iterable<ResourceGroup> myPastResources;
  private final Iterable<ResourceGroup> myPresentResources;

  public ResourcesSnapshotDelta(Iterable<ResourceGroup> pastResources, Iterable<ResourceGroup> presentResources) {
    super(SourceSnapshotImpl.composite(pastResources), SourceSnapshotImpl.composite(presentResources));
    myPastResources = pastResources;
    myPresentResources = presentResources;
  }

  @Override
  public @NotNull NodeSourceSnapshot getBaseSnapshot() {
    return (NodeSourceSnapshot)super.getBaseSnapshot();
  }

  public Iterable<ResourceGroup> getPastResources() {
    return myPastResources;
  }

  public Iterable<ResourceGroup> getPresentResources() {
    return myPresentResources;
  }
}

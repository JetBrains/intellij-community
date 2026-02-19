// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.impl.BuildRootIndexImpl;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.model.JpsModel;

public final class BuildTargetsState {
  final BuildTargetStateManager impl;

  /**
   * @deprecated temporary available to enable kotlin tests running. Should be removed eventually
   */
  @Deprecated
  @ApiStatus.Internal
  public BuildTargetsState(@NotNull BuildDataPaths dataPaths, JpsModel model, BuildRootIndexImpl ignored) {
    this(new BuildTargetStateManagerImpl(dataPaths, model));
  }

  @ApiStatus.Internal
  public BuildTargetsState(@NotNull BuildTargetStateManager impl) {
    this.impl = impl;
  }

  public void save() {
    impl.save();
  }

  @SuppressWarnings("unused")
  public int getBuildTargetId(@NotNull BuildTarget<?> target) {
    return impl.getBuildTargetId(target);
  }
}

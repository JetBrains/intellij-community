// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsModel;

/**
 * @see BuildTargetType#createLoader(JpsModel)
 */
public abstract class BuildTargetLoader<T extends BuildTarget<?>> {
  /**
   * Deserialize build target by its id (returned by {@link BuildTarget#getId()} method)
   */
  public abstract @Nullable T createTarget(@NotNull String targetId);
}

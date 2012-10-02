package org.jetbrains.jps.builders;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class BuildTargetLoader<T extends BuildTarget<?>> {
  @Nullable
  public abstract T createTarget(@NotNull String targetId);
}

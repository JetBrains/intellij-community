package org.jetbrains.jps.builders;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class BuildTargetLoader {
  @Nullable
  public abstract BuildTarget createTarget(@NotNull String targetId);
}

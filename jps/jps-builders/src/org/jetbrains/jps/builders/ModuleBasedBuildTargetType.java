package org.jetbrains.jps.builders;

import org.jetbrains.annotations.NotNull;

public abstract class ModuleBasedBuildTargetType<T extends ModuleBasedTarget<?>> extends BuildTargetType<T> implements ModuleInducedTargetType {
  protected ModuleBasedBuildTargetType(@NotNull String typeId) {
    super(typeId);
  }

  /**
   * @see BuildTargetType#BuildTargetType(String, boolean)
   */
  protected ModuleBasedBuildTargetType(@NotNull String typeId, boolean fileBased) {
    super(typeId, fileBased);
  }
}

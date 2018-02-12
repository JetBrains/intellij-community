package org.jetbrains.jps.builders;

public abstract class ModuleBasedBuildTargetType<T extends ModuleBasedTarget<?>> extends BuildTargetType<T> implements ModuleInducedTargetType {
  protected ModuleBasedBuildTargetType(String typeId) {
    super(typeId);
  }

  /**
   * @see BuildTargetType#BuildTargetType(String, boolean)
   */
  protected ModuleBasedBuildTargetType(String typeId, boolean fileBased) {
    super(typeId, fileBased);
  }
}

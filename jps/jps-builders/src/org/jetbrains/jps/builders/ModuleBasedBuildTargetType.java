package org.jetbrains.jps.builders;

public abstract class ModuleBasedBuildTargetType<T extends ModuleBasedTarget<?>> extends BuildTargetType<T>{
  protected ModuleBasedBuildTargetType(String typeId) {
    super(typeId);
  }
}

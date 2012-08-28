package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.model.module.JpsModule;

/**
 * @author nik
 */
public class RealModuleBuildTarget extends ModuleBuildTarget {
  private final JpsModule myModule;

  public RealModuleBuildTarget(@NotNull JpsModule module, JavaModuleBuildTargetType targetType) {
    super(module.getName(), targetType);
    myModule = module;
  }

  @NotNull
  public JpsModule getModule() {
    return myModule;
  }
}

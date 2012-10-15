package org.jetbrains.jps.builders.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;

/**
 * @author nik
 */
public abstract class ExcludedJavaSourceRootProvider {
  public abstract boolean isExcludedFromCompilation(@NotNull JpsModule module, @NotNull JpsModuleSourceRoot root);
}

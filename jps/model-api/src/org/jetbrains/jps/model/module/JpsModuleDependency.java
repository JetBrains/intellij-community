package org.jetbrains.jps.model.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public interface JpsModuleDependency extends JpsDependencyElement {
  @NotNull
  JpsModuleReference getModuleReference();

  @Nullable
  JpsModule getModule();
}

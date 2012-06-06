package org.jetbrains.jps.model.module;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public interface JpsModuleDependency extends JpsDependencyElement {
  @NotNull
  JpsModuleReference getModuleReference();
}

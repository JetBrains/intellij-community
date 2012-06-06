package org.jetbrains.jps.model.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementReference;

/**
 * @author nik
 */
public interface JpsModuleReference extends JpsElementReference<JpsModule> {
  @NotNull
  String getModuleName();
}

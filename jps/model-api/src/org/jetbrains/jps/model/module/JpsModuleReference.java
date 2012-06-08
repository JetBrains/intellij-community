package org.jetbrains.jps.model.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementReference;
import org.jetbrains.jps.model.JpsModel;

/**
 * @author nik
 */
public interface JpsModuleReference extends JpsElementReference<JpsModule> {
  @NotNull
  String getModuleName();

  @Override
  JpsModuleReference asExternal(@NotNull JpsModel model);
}

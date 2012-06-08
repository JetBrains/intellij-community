package org.jetbrains.jps.model.library;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementReference;
import org.jetbrains.jps.model.JpsModel;

/**
 * @author nik
 */
public interface JpsLibraryReference extends JpsElementReference<JpsLibrary> {
  @NotNull
  String getLibraryName();

  @Override
  JpsLibraryReference asExternal(@NotNull JpsModel model);
}

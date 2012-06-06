package org.jetbrains.jps.model.library;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementReference;

/**
 * @author nik
 */
public interface JpsLibraryReference extends JpsElementReference<JpsLibrary> {
  @NotNull
  String getLibraryName();
}

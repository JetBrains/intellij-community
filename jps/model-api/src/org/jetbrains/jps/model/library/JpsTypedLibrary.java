package org.jetbrains.jps.model.library;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementProperties;

/**
 * @author nik
 */
public interface JpsTypedLibrary<P extends JpsElementProperties> extends JpsLibrary {
  @NotNull
  @Override
  JpsLibraryType<P> getType();

  @NotNull
  P getProperties();
}

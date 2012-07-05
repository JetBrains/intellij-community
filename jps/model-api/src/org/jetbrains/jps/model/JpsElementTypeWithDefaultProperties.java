package org.jetbrains.jps.model;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public interface JpsElementTypeWithDefaultProperties<P extends JpsElementProperties> {
  @NotNull
  P createDefaultProperties();
}

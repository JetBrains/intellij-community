package org.jetbrains.jps.model;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public interface SimpleJpsElement<P extends JpsElementProperties> extends JpsElement {
  @NotNull
  P getProperties();

  void setProperties(@NotNull P properties);
}

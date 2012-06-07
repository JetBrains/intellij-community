package org.jetbrains.jps.model;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public interface JpsTypedData<P extends JpsElementProperties> extends JpsElement {
  @NotNull
  JpsElementType<P> getType();

  @NotNull
  P getProperties();

  void setProperties(@NotNull P properties);
}

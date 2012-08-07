package org.jetbrains.jps.model;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public interface JpsSimpleElement<P> extends JpsElement {
  @NotNull
  P getProperties();

  void setProperties(@NotNull P properties);
}

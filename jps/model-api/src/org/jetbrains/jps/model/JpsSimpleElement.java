package org.jetbrains.jps.model;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public interface JpsSimpleElement<D> extends JpsElement {
  @NotNull
  D getData();

  void setData(@NotNull D data);
}

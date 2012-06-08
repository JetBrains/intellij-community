package org.jetbrains.jps.model;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public interface JpsElementCreator<E extends JpsElement> {
  @NotNull
  E create();
}

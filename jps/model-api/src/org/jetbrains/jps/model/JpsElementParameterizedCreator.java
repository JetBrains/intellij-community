package org.jetbrains.jps.model;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public interface JpsElementParameterizedCreator<E extends JpsElement, P> {
  @NotNull
  E create(@NotNull P param);
}

package org.jetbrains.jps.model;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public interface JpsNamedElement extends JpsElement {
  @NotNull
  String getName();

  void setName(@NotNull String name);
}

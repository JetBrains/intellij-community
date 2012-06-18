package org.jetbrains.jps.model;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public interface JpsCompositeElement extends JpsElement {
  @NotNull
  JpsElementContainer getContainer();
}

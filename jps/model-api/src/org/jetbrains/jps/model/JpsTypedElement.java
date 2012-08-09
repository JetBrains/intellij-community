package org.jetbrains.jps.model;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public interface JpsTypedElement<P extends JpsElement> extends JpsElement {
  JpsElementType<?> getType();

  @NotNull
  P getProperties();
}

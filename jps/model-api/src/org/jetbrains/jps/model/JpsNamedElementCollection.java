package org.jetbrains.jps.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface JpsNamedElementCollection<E extends JpsNamedElement> extends JpsElementCollection<E> {
  /**
   * Returns a child from this collection which {@link JpsNamedElement#getName() name} is equal to {@code name} or {@code null} if there is
   * no such element.
   */
  @Nullable E findChild(@NotNull String name);
}

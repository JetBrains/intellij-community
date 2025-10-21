package org.jetbrains.jps.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A variant of {@link JpsElementCollection} for elements which have a name. 
 * It's supposed that all elements in the collection have different names, but the implementation doesn't enforce this.
 */
public interface JpsNamedElementCollection<E extends JpsNamedElement> extends JpsElementCollection<E> {
  /**
   * Returns a child from this collection which {@link JpsNamedElement#getName() name} is equal to {@code name} or {@code null} if there is
   * no such element.
   * <br>
   * It's assumed that the collection contains only one element with the given name. If this doesn't hold, an arbitrary element having
   * the given name will be returned.
   */
  @Nullable E findChild(@NotNull String name);
}

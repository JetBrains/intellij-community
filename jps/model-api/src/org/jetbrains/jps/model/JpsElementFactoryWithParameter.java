package org.jetbrains.jps.model;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public interface JpsElementFactoryWithParameter<E extends JpsElement, P> {
  @NotNull
  E create(@NotNull JpsModel model, @NotNull JpsEventDispatcher eventDispatcher, JpsParentElement parent, @NotNull P param);
}

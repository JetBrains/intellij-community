package org.jetbrains.jps.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public interface JpsElementReference<T extends JpsElement> extends JpsElement {
  @Nullable
  T resolve();

  JpsElementReference<T> asExternal(@NotNull JpsModel model);
}

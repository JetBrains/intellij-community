package org.jetbrains.jps.model;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public interface JpsModel {
  @NotNull
  JpsProject getProject();

  @NotNull
  JpsGlobal getGlobal();

  @NotNull
  JpsModel createModifiableModel(@NotNull JpsEventDispatcher eventDispatcher);

  void registerExternalReference(@NotNull JpsElementReference<?> reference);

  void commit();
}

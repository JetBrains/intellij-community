package org.jetbrains.jps.model;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public interface JpsElementContainer {
  <T extends JpsElement>
  T getChild(@NotNull JpsElementKind<T> kind);

  @NotNull
  <T extends JpsElement, K extends JpsElementKind<T>&JpsElementCreator<T>>
  T setChild(@NotNull K kind);

  @NotNull
  <T extends JpsElement, K extends JpsElementKind<T>&JpsElementCreator<T>>
  T getOrSetChild(@NotNull K kind);

  @NotNull
  <T extends JpsElement, P, K extends JpsElementKind<T>&JpsElementParameterizedCreator<T, P>>
  T setChild(@NotNull K kind, @NotNull P param);

  <T extends JpsElement>
  T setChild(JpsElementKind<T> kind, T child);

  <T extends JpsElement>
  void removeChild(@NotNull JpsElementKind<T> kind);
}

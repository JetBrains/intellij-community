package org.jetbrains.jps.model;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public interface JpsElementContainer {
  <T extends JpsElement>
  T getChild(@NotNull JpsElementChildRole<T> role);

  @NotNull
  <T extends JpsElement, K extends JpsElementChildRole<T> &JpsElementCreator<T>>
  T setChild(@NotNull K role);

  @NotNull
  <T extends JpsElement, K extends JpsElementChildRole<T> &JpsElementCreator<T>>
  T getOrSetChild(@NotNull K role);

  @NotNull
  <T extends JpsElement, P, K extends JpsElementChildRole<T> &JpsElementParameterizedCreator<T, P>>
  T setChild(@NotNull K role, @NotNull P param);

  <T extends JpsElement>
  T setChild(JpsElementChildRole<T> role, T child);

  <T extends JpsElement>
  void removeChild(@NotNull JpsElementChildRole<T> role);
}

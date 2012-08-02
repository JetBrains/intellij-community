package org.jetbrains.jps.model;

import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * @author nik
 */
public interface JpsEventDispatcher {
  @NotNull
  <T extends EventListener> T getPublisher(Class<T> listenerClass);

  void fireElementRenamed(@NotNull JpsNamedElement element, @NotNull String oldName, @NotNull String newName);

  void fireElementChanged(@NotNull JpsElement element);

  <T extends JpsElement>
  void fireElementAdded(@NotNull T element, @NotNull JpsElementChildRole<T> role);

  <T extends JpsElement>
  void fireElementRemoved(@NotNull T element, @NotNull JpsElementChildRole<T> role);
}

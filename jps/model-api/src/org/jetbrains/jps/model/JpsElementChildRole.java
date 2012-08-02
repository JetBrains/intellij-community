package org.jetbrains.jps.model;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class JpsElementChildRole<E extends JpsElement> {
  public void fireElementAdded(@NotNull JpsEventDispatcher dispatcher, @NotNull E element) {
  }

  public void fireElementRemoved(@NotNull JpsEventDispatcher dispatcher, @NotNull E element) {
  }
}

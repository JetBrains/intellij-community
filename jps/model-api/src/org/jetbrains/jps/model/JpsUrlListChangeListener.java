package org.jetbrains.jps.model;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public interface JpsUrlListChangeListener<T extends JpsElement> {
  void urlAdded(@NotNull T element, @NotNull String url);

  void urlRemoved(@NotNull T element, @NotNull String url);
}

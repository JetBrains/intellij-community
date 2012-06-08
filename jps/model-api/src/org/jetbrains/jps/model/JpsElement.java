package org.jetbrains.jps.model;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public interface JpsElement {
  @NotNull
  BulkModificationSupport<?> getBulkModificationSupport();

  interface BulkModificationSupport<E extends JpsElement> extends JpsElement {
    @NotNull
    E createCopy();

    void applyChanges(@NotNull E modified);
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model;

import org.jetbrains.annotations.NotNull;

public interface JpsElement {
  /**
   * @deprecated modifications of JpsModel were never fully supported, and they won't be since JpsModel will be superseded by {@link com.intellij.platform.workspace.storage.EntityStorage the workspace model}.
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  default @NotNull BulkModificationSupport<?> getBulkModificationSupport() {
    throw new UnsupportedOperationException();
  }

  /**
   * @deprecated modifications of JpsModel were never fully supported, and they won't be since JpsModel will be superseded by {@link com.intellij.platform.workspace.storage.EntityStorage the workspace model}.
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  interface BulkModificationSupport<E extends JpsElement> extends JpsElement {
    /**
     * @deprecated creating copies isn't supported on the model level, create you own methods if you need to have this functionality for 
     * specific elements.
     */
    @Deprecated(forRemoval = true)
    default @NotNull E createCopy() {
      throw new UnsupportedOperationException();
    }

    /**
     * @deprecated modifications aren't supported on the model level, create you own methods if you need to have this functionality for 
     * specific elements.
     */
    @Deprecated(forRemoval = true)
    default void applyChanges(@NotNull E modified) {
    }
  }
}

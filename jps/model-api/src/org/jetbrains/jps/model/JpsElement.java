/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.model;

import org.jetbrains.annotations.NotNull;

public interface JpsElement {
  /**
   * @deprecated modifications of JpsModel were never fully supported, and they won't be since JpsModel will be superseded by {@link com.intellij.platform.workspace.storage.WorkspaceEntityStorage the workspace model}.
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  @NotNull
  default BulkModificationSupport<?> getBulkModificationSupport() {
    throw new UnsupportedOperationException();
  }

  /**
   * @deprecated modifications of JpsModel were never fully supported, and they won't be since JpsModel will be superseded by {@link com.intellij.platform.workspace.storage.WorkspaceEntityStorage the workspace model}.
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  interface BulkModificationSupport<E extends JpsElement> extends JpsElement {
    /**
     * @deprecated creating copies isn't supported on the model level, create you own methods if you need to have this functionality for 
     * specific elements.
     */
    @Deprecated(forRemoval = true)
    @NotNull
    default E createCopy() {
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

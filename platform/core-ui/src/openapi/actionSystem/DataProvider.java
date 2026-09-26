// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Use {@link UiDataProvider} instead.
 * <p>
 * Allows a component hosting actions to provide context information to the actions. When a specific
 * data item is requested, the component hierarchy is walked up from the currently focused component,
 * and every component implementing the {@code DataProvider} interface is queried for the data
 * until one of them returns the data. Data items can also be mapped to each other - for example,
 * if a data provider provides an item for {@link CommonDataKeys#NAVIGATABLE}, an item for
 * {@link CommonDataKeys#NAVIGATABLE_ARRAY} can be generated from it automatically.
 *
 * @see DataContext
 */
@ApiStatus.Obsolete
@ApiStatus.OverrideOnly
@FunctionalInterface
public interface DataProvider {
  /**
   * Returns the object corresponding to the specified data identifier. Some of the supported
   * data identifiers are defined in the {@link com.intellij.openapi.actionSystem.PlatformDataKeys} class.
   *
   * @param dataId the data identifier for which the value is requested.
   * @return the value, or null if no value is available in the current context for this identifier.
   */
  @Nullable
  Object getData(@NotNull @NonNls String dataId);
}

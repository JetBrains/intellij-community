// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.favoritesTreeView;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Returns the nodes which should be added to the Favorites for the given data context.
 * Implementations of this class must be registered as extensions for
 * {@code com.intellij.favoriteNodeProvider} extension point.
 *
 * @deprecated Use Bookmarks API instead.
 */
@Deprecated(forRemoval = true)
public abstract class FavoriteNodeProvider {
  public static final ExtensionPointName<FavoriteNodeProvider> EP_NAME = new ExtensionPointName<>("com.intellij.favoriteNodeProvider");

  /**
   * Returns the identifier used to persist favorites for this provider.
   *
   * @return the string identifier.
   */
  public abstract @NotNull @NonNls String getFavoriteTypeId();
}

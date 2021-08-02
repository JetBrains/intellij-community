// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.facet;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.roots.ProjectModelExternalSource;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.NonExtendable
public interface ModifiableFacetModel extends FacetModel {

  void addFacet(Facet<?> facet);
  void addFacet(Facet<?> facet, @Nullable ProjectModelExternalSource externalSource);
  void removeFacet(Facet<?> facet);

  /**
   * Replaces {@code original} facet by {@code replacement}. The only difference with {@code removeFacet(original); addFacet(replacement); }
   * is that this method preserves order of facets in internal structures to avoid modifications of *.iml files.
   */
  @ApiStatus.Internal
  void replaceFacet(@NotNull Facet<?> original, @NotNull Facet<?> replacement);

  void rename(Facet<?> facet, String newName);

  @Nullable
  String getNewName(Facet<?> facet);

  void commit();

  boolean isModified();

  boolean isNewFacet(Facet<?> facet);

  void addListener(@NotNull Listener listener, @NotNull Disposable parentDisposable);

  @FunctionalInterface
  interface Listener {
    void onChanged();
  }
}

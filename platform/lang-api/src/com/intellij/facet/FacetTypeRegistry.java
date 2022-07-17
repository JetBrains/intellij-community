// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.facet;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class FacetTypeRegistry {
  public static FacetTypeRegistry getInstance() {
    return ApplicationManager.getApplication().getService(FacetTypeRegistry.class);
  }

  /**
   * @deprecated register {@code facetType} as an extension instead
   */
  @Deprecated(forRemoval = true)
  public abstract void registerFacetType(FacetType<?, ?> facetType);

  public abstract FacetTypeId<?> @NotNull [] getFacetTypeIds();

  public abstract FacetType<?, ?> @NotNull [] getFacetTypes();

  public abstract FacetType<?, ?> @NotNull [] getSortedFacetTypes();

  @Nullable
  public abstract FacetType findFacetType(String id);

  @NotNull
  public abstract <F extends Facet<C>, C extends FacetConfiguration> FacetType<F, C> findFacetType(@NotNull FacetTypeId<F> typeId);
}

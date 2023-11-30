// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.facet;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class FacetTypeRegistry {
  public static FacetTypeRegistry getInstance() {
    return ApplicationManager.getApplication().getService(FacetTypeRegistry.class);
  }

  public abstract FacetTypeId<?> @NotNull [] getFacetTypeIds();

  public abstract FacetType<?, ?> @NotNull [] getFacetTypes();

  public abstract FacetType<?, ?> @NotNull [] getSortedFacetTypes();

  public abstract @Nullable FacetType findFacetType(String id);

  public abstract @NotNull <F extends Facet<C>, C extends FacetConfiguration> FacetType<F, C> findFacetType(@NotNull FacetTypeId<F> typeId);
}

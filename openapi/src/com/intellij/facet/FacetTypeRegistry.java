/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet;

import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class FacetTypeRegistry {

  public static FacetTypeRegistry getInstance() {
    return ServiceManager.getService(FacetTypeRegistry.class);
  }

  public abstract void registerFacetType(FacetType facetType);
  public abstract void unregisterFacetType(FacetType facetType);

  public abstract FacetTypeId[] getFacetTypeIds();

  public abstract FacetType[] getFacetTypes();

  @Nullable
  public abstract FacetType findFacetType(String id);

  @Nullable
  public abstract <F extends Facet<C>, C extends FacetConfiguration> FacetType<F, C> findFacetType(FacetTypeId<F> typeId);
}

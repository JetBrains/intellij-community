/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class FacetTypeRegistry {

  public static FacetTypeRegistry getInstance() {
    return ApplicationManager.getApplication().getComponent(FacetTypeRegistry.class);
  }

  public abstract void registerFacetType(FacetType facetType);
  public abstract void unregisterFacetType(FacetType facetType);

  public abstract FacetType[] getFacetTypes();

  @Nullable
  public abstract FacetType findFacetType(String id);

  @Nullable
  public abstract FacetType findFacetType(FacetTypeId typeId);
}

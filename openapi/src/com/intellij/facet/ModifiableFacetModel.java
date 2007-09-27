/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.Disposable;

/**
 * @author nik
 */
public interface ModifiableFacetModel extends FacetModel {

  void addFacet(Facet facet);
  void removeFacet(Facet facet);

  void rename(Facet facet, String newName);

  @Nullable
  String getNewName(Facet facet);

  void commit();

  boolean isModified();

  boolean isNewFacet(Facet facet);
  
  String getFacetName(Facet facet);

  void addListener(@NotNull Listener listener, @Nullable Disposable parentDisposable);

  interface Listener {
    void onChanged();
  }
}

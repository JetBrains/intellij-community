// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.facet.pointers;

import com.intellij.facet.Facet;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public abstract class FacetPointersManager {
  public static FacetPointersManager getInstance(Project project) {
    return project.getComponent(FacetPointersManager.class);
  }

  public abstract <F extends Facet> FacetPointer<F> create(F facet);
  public abstract <F extends Facet> FacetPointer<F> create(String id);

  public abstract void addListener(FacetPointerListener<Facet> listener);
  public abstract void addListener(FacetPointerListener<Facet> listener, Disposable parentDisposable);
  public abstract void removeListener(FacetPointerListener<Facet> listener);

  public abstract <F extends Facet> void addListener(Class<F> facetClass, FacetPointerListener<F> listener);
  public abstract <F extends Facet> void addListener(Class<F> facetClass, FacetPointerListener<F> listener, Disposable parentDisposable);
  public abstract <F extends Facet> void removeListener(Class<F> facetClass, FacetPointerListener<F> listener);

  public static String constructId(final String moduleName, final String facetTypeId, final String facetName) {
    return moduleName + "/" + facetTypeId + "/" + facetName;
  }

  @NotNull
  public static String constructId(@NotNull Facet facet) {
    return constructId(facet.getModule().getName(), facet.getType().getStringId(), facet.getName());
  }

  @NotNull
  public static String getFacetName(@NotNull String facetPointerId) {
    return facetPointerId.substring(facetPointerId.lastIndexOf('/') + 1);
  }

  @NotNull
  public static String getModuleName(String facetPointerId) {
    return facetPointerId.substring(0, facetPointerId.indexOf('/'));
  }

  @NotNull
  public static String getFacetType(@NotNull String facetPointerId) {
    return facetPointerId.substring(facetPointerId.indexOf('/') + 1, facetPointerId.lastIndexOf('/'));
  }
}

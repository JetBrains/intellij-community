/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class Facet<C extends FacetConfiguration> implements Disposable {
  public static final Facet[] EMPTY_ARRAY = new Facet[0];
  private final @NotNull FacetType myFacetType;
  private final @NotNull Module myModule;
  private final @NotNull C myConfiguration;
  private final Facet myUnderlyingFacet;
  private boolean myImplicit;

  public Facet(@NotNull final FacetType facetType, @NotNull final Module module, @NotNull final C configuration, Facet underlyingFacet) {
    myFacetType = facetType;
    myModule = module;
    myConfiguration = configuration;
    myUnderlyingFacet = underlyingFacet;
  }

  @NotNull
  public final FacetType getType() {
    return myFacetType;
  }

  public final FacetTypeId getTypeId() {
    return myFacetType.getId();
  }

  public final Facet getUnderlyingFacet() {
    return myUnderlyingFacet;
  }

  @NotNull
  public C getConfiguration() {
    return myConfiguration;
  }

  @NotNull
  public final Module getModule() {
    return myModule;
  }

  public boolean isImplicit() {
    return myImplicit;
  }

  public void setImplicit(final boolean implicit) {
    myImplicit = implicit;
  }

  public void initFacet() {
  }

  public void disposeFacet() {
  }


  public final void dispose() {
    disposeFacet();
  }

  public final int hashCode() {
    return super.hashCode();
  }

  @SuppressWarnings({"EqualsWhichDoesntCheckParameterClass"})
  public final boolean equals(Object obj) {
    return super.equals(obj);
  }

  public String getPresentableName() {
    return myFacetType.getPresentableName();
  }
}

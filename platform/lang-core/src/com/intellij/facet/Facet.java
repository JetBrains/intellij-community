// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.facet;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ProjectModelElement;
import com.intellij.openapi.roots.ProjectModelExternalSource;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a specific instance of facet
 *
 * @see FacetType
 */
public class Facet<C extends FacetConfiguration> extends UserDataHolderBase implements UserDataHolder, Disposable, ProjectModelElement {
  public static final Facet[] EMPTY_ARRAY = new Facet[0];
  @NotNull private final FacetType myFacetType;
  @NotNull private final Module myModule;
  @NotNull private final C myConfiguration;
  private final Facet myUnderlyingFacet;
  private @NlsSafe String myName;
  private boolean isDisposed;
  private ProjectModelExternalSource myExternalSource;

  public Facet(@NotNull final FacetType facetType,
               @NotNull final Module module,
               @NotNull final @NlsSafe String name,
               @NotNull final C configuration,
               Facet underlyingFacet) {
    myName = name;
    myFacetType = facetType;
    myModule = module;
    myConfiguration = configuration;
    myUnderlyingFacet = underlyingFacet;
    Disposer.register(myModule, this);
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
  public final C getConfiguration() {
    return myConfiguration;
  }

  @NotNull
  public final Module getModule() {
    return myModule;
  }

  public boolean isDisposed() {
    return isDisposed;
  }

  /**
   * Called when the module containing this facet is initialized
   */
  public void initFacet() {
  }

  /**
   * Called when the module containing this facet is disposed
   */
  public void disposeFacet() {
  }

  @Override
  public final void dispose() {
    assert !isDisposed;
    isDisposed = true;
    disposeFacet();
  }

  public final int hashCode() {
    return super.hashCode();
  }

  public final boolean equals(Object obj) {
    return super.equals(obj);
  }

  @NotNull
  public final @NlsSafe String getName() {
    return myName;
  }

  /**
   * Use {@link ModifiableFacetModel#rename} to rename facets
   */
  final void setName(@NotNull final @NlsSafe String name) {
    myName = name;
  }

  @Nullable
  @Override
  public ProjectModelExternalSource getExternalSource() {
    return myExternalSource;
  }

  /**
   * This method marked as public only for the [FacetManagerBridge] and shouldn't be used anywhere outside
   */
  @ApiStatus.Internal
  public void setExternalSource(ProjectModelExternalSource externalSource) {
    myExternalSource = externalSource;
  }

  @Override
  public String toString() {
    return getName() + " (" + getModule().getName() + ")";
  }
}

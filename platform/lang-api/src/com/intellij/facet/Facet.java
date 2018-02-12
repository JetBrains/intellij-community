/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.facet;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ProjectModelElement;
import com.intellij.openapi.roots.ProjectModelExternalSource;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a specific instance of facet
 *
 * @see FacetType
 *
 * @author nik
 */
public class Facet<C extends FacetConfiguration> extends UserDataHolderBase implements UserDataHolder, Disposable, ProjectModelElement {
  public static final Facet[] EMPTY_ARRAY = new Facet[0];
  @NotNull private final FacetType myFacetType;
  @NotNull private final Module myModule;
  @NotNull private final C myConfiguration;
  private final Facet myUnderlyingFacet;
  private String myName;
  private boolean isDisposed;
  private ProjectModelExternalSource myExternalSource;

  public Facet(@NotNull final FacetType facetType, @NotNull final Module module, @NotNull final String name, @NotNull final C configuration, Facet underlyingFacet) {
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

  @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
  public final boolean equals(Object obj) {
    return super.equals(obj);
  }

  @NotNull
  public final String getName() {
    return myName;
  }

  /**
   * Use {@link ModifiableFacetModel#rename} to rename facets
   */
  final void setName(@NotNull final String name) {
    myName = name;
  }

  @Nullable
  @Override
  public ProjectModelExternalSource getExternalSource() {
    return myExternalSource;
  }

  void setExternalSource(ProjectModelExternalSource externalSource) {
    myExternalSource = externalSource;
  }

  @Override
  public String toString() {
    return getName() + " (" + getModule().getName() + ")";
  }
}

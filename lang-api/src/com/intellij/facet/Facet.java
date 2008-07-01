/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 *
 * @see FacetType
 */
public class Facet<C extends FacetConfiguration> extends UserDataHolderBase implements UserDataHolder, Disposable {
  public static final Facet[] EMPTY_ARRAY = new Facet[0];
  private final @NotNull FacetType myFacetType;
  private final @NotNull Module myModule;
  private final @NotNull C myConfiguration;
  private final Facet myUnderlyingFacet;
  private String myName;
  private boolean myImplicit;

  public Facet(@NotNull final FacetType facetType, @NotNull final Module module, final String name, @NotNull final C configuration, Facet underlyingFacet) {
    myName = name;
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
  public final C getConfiguration() {
    return myConfiguration;
  }

  @NotNull
  public final Module getModule() {
    return myModule;
  }

  @Deprecated
  public final boolean isImplicit() {
    return myImplicit;
  }

  @Deprecated
  public final void setImplicit(final boolean implicit) {
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


  public final String getName() {
    return myName;
  }

  /**
   * use {@link com.intellij.facet.ModifiableFacetModel#rename} to rename facet
   */
  final void setName(final String name) {
    myName = name;
  }

}

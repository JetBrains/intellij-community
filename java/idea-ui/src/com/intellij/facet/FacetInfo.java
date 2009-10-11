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

import org.jetbrains.annotations.Nullable;

/**
 * @author nik
*/
//todo[nik] delete
public final class FacetInfo {
  public static final FacetInfo[] EMPTY_ARRAY = new FacetInfo[0];
  private final FacetType myFacetType;
  private final FacetConfiguration myConfiguration;
  private final FacetInfo myUnderlyingFacet;
  private String myName;

  public FacetInfo(final FacetType facetType, String name, final FacetConfiguration configuration, final FacetInfo underlyingFacet) {
    myName = name;
    myFacetType = facetType;
    myConfiguration = configuration;
    myUnderlyingFacet = underlyingFacet;
  }

  public FacetType getFacetType() {
    return myFacetType;
  }

  public FacetConfiguration getConfiguration() {
    return myConfiguration;
  }

  @Nullable
  public FacetInfo getUnderlyingFacet() {
    return myUnderlyingFacet;
  }

  public String getName() {
    return myName;
  }

  public void setName(final String name) {
    myName = name;
  }
}

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
package com.intellij.facet.impl.autodetecting.model;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.FacetType;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class FacetInfoBackedByFacet implements FacetInfo2<Module> {
  private final Facet myFacet;
  private final ProjectFacetInfoSet myProjectFacetSet;

  FacetInfoBackedByFacet(@NotNull Facet facet, final ProjectFacetInfoSet projectFacetSet) {
    myFacet = facet;
    myProjectFacetSet = projectFacetSet;
  }

  @NotNull
  public String getFacetName() {
    return myFacet.getName();
  }

  @NotNull
  public FacetConfiguration getConfiguration() {
    return myFacet.getConfiguration();
  }

  @NotNull
  public FacetType getFacetType() {
    return myFacet.getType();
  }

  public FacetInfo2<Module> getUnderlyingFacetInfo() {
    Facet underlying = myFacet.getUnderlyingFacet();
    return underlying != null ? myProjectFacetSet.getOrCreateInfo(underlying) : null;
  }

  @NotNull
  public Module getModule() {
    return myFacet.getModule();
  }

  public Facet getFacet() {
    return myFacet;
  }
}

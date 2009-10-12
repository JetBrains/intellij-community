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

package com.intellij.facet.impl;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetManagerImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ConfigurationErrorDescription;
import com.intellij.openapi.project.ProjectBundle;

/**
 * @author nik
 */
public class FacetLoadingErrorDescription extends ConfigurationErrorDescription {
  private final Facet myUnderlyingFacet;
  private final FacetState myState;
  private final Module myModule;

  public FacetLoadingErrorDescription(final Module module, final String errorMessage, final Facet underlyingFacet, final FacetState state) {
    super(state.getName() + " (" + module.getName() + ")", ProjectBundle.message("element.kind.name.facet"), errorMessage);
    myModule = module;
    myUnderlyingFacet = underlyingFacet;
    myState = state;
  }

  public Facet getUnderlyingFacet() {
    return myUnderlyingFacet;
  }

  public FacetState getState() {
    return myState;
  }

  public Module getModule() {
    return myModule;
  }

  @Override
  public String getRemoveConfirmationMessage() {
    return ProjectBundle.message("confirmation.message.would.you.like.to.remove.facet", myState.getName(), myModule.getName());
  }

  @Override
  public void removeInvalidElement() {
    FacetManagerImpl manager = (FacetManagerImpl)FacetManagerImpl.getInstance(myModule);
    manager.removeInvalidFacet(myUnderlyingFacet, myState);
  }

  @Override
  public boolean isValid() {
    return !myModule.isDisposed();
  }
}

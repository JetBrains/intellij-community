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

package com.intellij.facet.impl;

import com.intellij.facet.Facet;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.RemoveInvalidElementsDialog;

/**
 * @author nik
 */
public class FacetLoadingErrorDescription implements RemoveInvalidElementsDialog.ErrorDescription {
  private final Facet myUnderlyingFacet;
  private final FacetState myState;
  private final String myErrorMessage;
  private final Module myModule;

  public FacetLoadingErrorDescription(final Module module, final String errorMessage, final Facet underlyingFacet, final FacetState state) {
    myModule = module;
    myErrorMessage = errorMessage;
    myUnderlyingFacet = underlyingFacet;
    myState = state;
  }

  public Facet getUnderlyingFacet() {
    return myUnderlyingFacet;
  }

  public FacetState getState() {
    return myState;
  }

  public String getErrorMessage() {
    return myErrorMessage;
  }

  public Module getModule() {
    return myModule;
  }

  public String getDescription() {
    return myErrorMessage;
  }

  public String getElementName() {
    return myState.getName() + " (" + myModule.getName() + ")";
  }
}

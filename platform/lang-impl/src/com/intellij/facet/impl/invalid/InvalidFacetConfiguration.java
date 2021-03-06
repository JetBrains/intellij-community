// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.facet.impl.invalid;

import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.serialization.facet.FacetState;

public class InvalidFacetConfiguration implements FacetConfiguration {
  private final FacetState myFacetState;
  private final @NlsContexts.DialogMessage String myErrorMessage;

  public InvalidFacetConfiguration(FacetState facetState, @NlsContexts.DialogMessage String errorMessage) {
    myFacetState = facetState;
    myErrorMessage = errorMessage;
  }

  public @NotNull FacetState getFacetState() {
    return myFacetState;
  }

  @Override
  public FacetEditorTab[] createEditorTabs(FacetEditorContext editorContext, FacetValidatorsManager validatorsManager) {
    return new FacetEditorTab[] {
      new InvalidFacetEditor(editorContext, myErrorMessage)
    };
  }

  public @NlsContexts.DialogMessage String getErrorMessage() {
    return myErrorMessage;
  }
}

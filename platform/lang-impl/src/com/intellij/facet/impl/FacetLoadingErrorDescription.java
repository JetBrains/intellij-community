// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.facet.impl;

import com.intellij.facet.FacetManagerBase;
import com.intellij.facet.impl.invalid.InvalidFacet;
import com.intellij.facet.impl.invalid.InvalidFacetManager;
import com.intellij.openapi.module.ConfigurationErrorDescription;
import com.intellij.openapi.module.ConfigurationErrorType;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class FacetLoadingErrorDescription extends ConfigurationErrorDescription {

  public static final class FacetErrorType extends ConfigurationErrorType {

    private static final FacetErrorType INSTANCE = new FacetErrorType();

    private FacetErrorType() { super(true); }

    @Override
    public @NotNull String getFeatureType() {
      return FacetManagerBase.FEATURE_TYPE;
    }

    @Override
    public @Nls @NotNull String getErrorText(int errorCount, @NlsSafe String firstElementName) {
      return ProjectBundle.message("facet.configuration.problem.text", errorCount, firstElementName);
    }
  }

  private final InvalidFacet myFacet;

  public FacetLoadingErrorDescription(final InvalidFacet facet) {
    super(facet.getName() + " (" + facet.getModule().getName() + ")", facet.getErrorMessage());
    myFacet = facet;
  }

  @Override
  public @NotNull String getIgnoreConfirmationMessage() {
    return ProjectBundle.message("confirmation.message.would.you.like.to.ignore.facet", myFacet.getName(), myFacet.getModule().getName());
  }

  @Override
  public void ignoreInvalidElement() {
    InvalidFacetManager.getInstance(myFacet.getModule().getProject()).setIgnored(myFacet, true);
  }

  @Override
  public boolean isValid() {
    return !myFacet.isDisposed();
  }

  @Override
  public @NonNls @NotNull String getImplementationName() {
    return myFacet.getName();
  }

  @Override
  public @NotNull FacetErrorType getErrorType() {
    return FacetErrorType.INSTANCE;
  }
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.facet.impl;

import com.intellij.facet.impl.invalid.InvalidFacet;
import com.intellij.facet.impl.invalid.InvalidFacetManager;
import com.intellij.openapi.module.ConfigurationErrorDescription;
import com.intellij.openapi.module.ConfigurationErrorType;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.projectModel.ProjectModelBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class FacetLoadingErrorDescription extends ConfigurationErrorDescription {
  private static final ConfigurationErrorType FACET_ERROR = new ConfigurationErrorType(true) {
    @Override
    public @Nls @NotNull String getErrorText(int errorCount, @NlsSafe String firstElementName) {
      return ProjectBundle.message("facet.configuration.problem.text", errorCount, firstElementName);
    }
  };

  private final InvalidFacet myFacet;

  public FacetLoadingErrorDescription(final InvalidFacet facet) {
    super(facet.getName() + " (" + facet.getModule().getName() + ")", facet.getErrorMessage(), FACET_ERROR);
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
}

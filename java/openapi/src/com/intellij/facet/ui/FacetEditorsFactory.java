// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.facet.ui;

import com.intellij.facet.ui.libraries.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

public abstract class FacetEditorsFactory {
  public static FacetEditorsFactory getInstance() {
    return ApplicationManager.getApplication().getService(FacetEditorsFactory.class);
  }


  public abstract FacetLibrariesValidator createLibrariesValidator(LibraryInfo @NotNull [] libraries,
                                                                   FacetLibrariesValidatorDescription description,
                                                                   FacetEditorContext context,
                                                                   final FacetValidatorsManager validatorsManager);

  public abstract FacetLibrariesValidator createLibrariesValidator(final LibraryInfo @NotNull [] libraries,
                                                                   @NotNull final Module module,
                                                                   @NotNull final String libraryName);

  public abstract LibrariesValidationComponent createLibrariesValidationComponent(LibraryInfo[] libraryInfos, Module module,
                                                                         String defaultLibraryName);

  public abstract MultipleFacetEditorHelper createMultipleFacetEditorHelper();
}

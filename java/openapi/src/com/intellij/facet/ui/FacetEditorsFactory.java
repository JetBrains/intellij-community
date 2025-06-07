// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.facet.ui;

import com.intellij.facet.ui.libraries.FacetLibrariesValidator;
import com.intellij.facet.ui.libraries.FacetLibrariesValidatorDescription;
import com.intellij.facet.ui.libraries.LibrariesValidationComponent;
import com.intellij.facet.ui.libraries.LibraryInfo;
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
                                                                   final @NotNull Module module,
                                                                   final @NotNull String libraryName);

  public abstract LibrariesValidationComponent createLibrariesValidationComponent(LibraryInfo[] libraryInfos, Module module,
                                                                         String defaultLibraryName);

  public abstract MultipleFacetEditorHelper createMultipleFacetEditorHelper();
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.facet.impl.ui;

import com.intellij.facet.impl.ui.libraries.*;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorsFactory;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.facet.ui.MultipleFacetEditorHelper;
import com.intellij.facet.ui.libraries.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription;
import org.jetbrains.annotations.NotNull;

public final class FacetEditorsFactoryImpl extends FacetEditorsFactory {
  public static FacetEditorsFactoryImpl getInstanceImpl() {
    return (FacetEditorsFactoryImpl)getInstance();
  }

  public FrameworkLibraryValidator createLibraryValidator(@NotNull CustomLibraryDescription libraryDescription,
                                                          @NotNull FacetEditorContext context,
                                                          @NotNull FacetValidatorsManager validatorsManager,
                                                          @NotNull String libraryCategory) {
    return createLibraryValidator(libraryDescription, new DelegatingLibrariesValidatorContext(context), validatorsManager, libraryCategory);
  }
  public FrameworkLibraryValidator createLibraryValidator(@NotNull CustomLibraryDescription libraryDescription,
                                                          @NotNull LibrariesValidatorContext context,
                                                          @NotNull FacetValidatorsManager validatorsManager,
                                                          @NotNull String libraryCategory) {
    return new FrameworkLibraryValidatorImpl(libraryDescription, context, validatorsManager, libraryCategory);
  }

  @Override
  public FacetLibrariesValidator createLibrariesValidator(final LibraryInfo @NotNull [] libraries, final FacetLibrariesValidatorDescription description,
                                                          final FacetEditorContext context,
                                                          final FacetValidatorsManager validatorsManager) {
    return new FacetLibrariesValidatorImpl(libraries, description, new DelegatingLibrariesValidatorContext(context), validatorsManager);
  }

  @Override
  public FacetLibrariesValidator createLibrariesValidator(final LibraryInfo @NotNull [] libraries, final @NotNull Module module, final @NotNull String libraryName) {
    return new FacetLibrariesValidatorImpl(libraries, new FacetLibrariesValidatorDescription(libraryName), new LibrariesValidatorContextImpl(module), null);
  }

  @Override
  public LibrariesValidationComponent createLibrariesValidationComponent(LibraryInfo[] libraryInfos, Module module,
                                                                         String defaultLibraryName) {
    return new LibrariesValidationComponentImpl(libraryInfos, module, defaultLibraryName);
  }

  @Override
  public MultipleFacetEditorHelper createMultipleFacetEditorHelper() {
    return new MultipleFacetEditorHelperImpl();
  }
}
